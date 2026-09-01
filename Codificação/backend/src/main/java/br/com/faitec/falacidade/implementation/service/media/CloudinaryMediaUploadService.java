package br.com.faitec.falacidade.implementation.service.media;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UploadStatus;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================
 * COMO O CLOUDINARY FUNCIONA NESTE SERVIÇO
 * ============================================================
 *
 * 1. UPLOAD ASSÍNCRONO
 *    O método uploadAsync() é anotado com @Async("uploadExecutor"),
 *    ou seja, roda em uma thread separada do pool dedicado ao Cloudinary.
 *    O controller retorna 202 imediatamente e o front-end faz polling
 *    em GET /api/occurrence/upload-status/{uploadId} até receber DONE/REJECTED/ERROR.
 *
 * 2. DETECÇÃO DE DESFOQUE (quality_analysis)
 *    O parâmetro "quality_analysis: true" ativa a análise de qualidade do Cloudinary.
 *    O retorno inclui: { "quality_analysis": { "focus": 0.82 } }
 *    O valor "focus" vai de 0 (totalmente borrada) a 1 (nítida).
 *    Limiar: focus < 0.3 → imagem considerada borrada.
 *
 *    Categorias CRÍTICAS (MAUS_TRATOS_AOS_ANIMAIS, PESSOA_PRECISANDO_DE_AJUDA):
 *      → Foto borrada é REJEITADA. A imagem é deletada do Cloudinary imediatamente.
 *      → O cidadão precisa tirar outra foto mais nítida.
 *    Demais categorias:
 *      → Foto borrada é ACEITA com o flag imageBlurred=true como aviso ao gestor.
 *
 * 3. ANONIMIZAÇÃO DE ROSTOS E PLACAS (desfoque na URL de entrega)
 *    Aplicada como transformação na URL de ENTREGA (ver applyPrivacyBlur):
 *      - e_blur_faces             → desfoca rostos detectados;
 *      - e_blur_region,g_ocr_text → desfoca todo texto detectado (inclui placas).
 *    O Cloudinary resolve isso no momento do download — não usa eager nem
 *    armazena o original "cru" sem proteção.
 *
 *    Por que fazer isso?
 *      - LGPD: rostos de pessoas são dados biométricos — não podemos expor sem consentimento.
 *      - Placas identificam veículos — podem vincular pessoas a locais específicos.
 *      - A denúncia precisa da cena, não das identidades presentes nela.
 *
 *    ATENÇÃO — cada efeito depende de um add-on do Cloudinary (ambos com nível gratuito):
 *      - Rostos: Dashboard → Add-ons → "Advanced Facial Attribute Detection".
 *      - Placas: Dashboard → Add-ons → "OCR Text Detection and Extraction".
 *      Sem o add-on, o upload funciona normalmente, mas aquele desfoque não é aplicado
 *      (a imagem é entregue sem erro). O OCR borra QUALQUER texto, não só a placa do veículo.
 * ============================================================
 */
@Service
public class CloudinaryMediaUploadService implements MediaUploadService {

    private static final double BLUR_THRESHOLD = 0.3;
    private static final long   MAX_AGE_MS     = 10 * 60 * 1000L; // 10 min

    private static final Set<Occurrence.OccurrenceType> CRITICAL_TYPES = Set.of(
        Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS,
        Occurrence.OccurrenceType.PESSOA_PRECISANDO_DE_AJUDA
    );

    private static final String REJECTION_MESSAGE =
        "A foto está borrada e não pode ser aceita para este tipo de denúncia. " +
        "Por favor, tire uma nova foto com melhor nitidez.";

    private final Cloudinary cloudinary;

    /**
     * Liga/desliga o desfoque de TEXTO/placas (e_blur_region,g_ocr_text), que depende do
     * add-on "OCR Text Detection and Extraction". Default: true. Desative (=false) se o
     * add-on não estiver ativo ou a cota grátis esgotar — assim as imagens não quebram.
     * O desfoque de ROSTOS (e_blur_faces) é nativo do Cloudinary e fica sempre ligado.
     */
    @Value("${cloudinary.blur-plates:true}")
    private boolean blurPlates;

    private final ConcurrentHashMap<String, UploadStatus> statusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>         createdAt = new ConcurrentHashMap<>();

    public CloudinaryMediaUploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // ------------------------------------------------------------------
    // ASSÍNCRONO — roda na thread do pool "uploadExecutor"
    // O @Async precisa estar aqui porque o CloudinaryMediaUploadService
    // é chamado diretamente pelo controller. Se estivesse no uploadSync()
    // (método interno), o Spring não interceptaria o proxy e o @Async
    // seria ignorado (auto-invocação não funciona com Spring AOP).
    // ------------------------------------------------------------------

    @Override
    @Async("uploadExecutor")
    public void uploadAsync(byte[] fileBytes, Occurrence.OccurrenceType type, String uploadId) {
        UploadStatus status = new UploadStatus(uploadId);
        statusMap.put(uploadId, status);
        createdAt.put(uploadId, System.currentTimeMillis());

        try {
            UploadResult result = uploadSync(fileBytes, type);

            if (result.rejected()) {
                status.markRejected(result.rejectionReason());
            } else {
                status.markDone(result.publicId(), result.url(), result.blurred());
            }

        } catch (Exception e) {
            status.markError("Falha ao processar a imagem: " + e.getMessage());
        } finally {
            evictExpiredEntries();
        }
    }

    @Override
    public UploadStatus getUploadStatus(String uploadId) {
        return statusMap.get(uploadId);
    }

    // ------------------------------------------------------------------
    // SÍNCRONO — lógica de upload + detecção de blur + anonimização
    // ------------------------------------------------------------------

    @Override
    public UploadResult uploadSync(byte[] fileBytes, Occurrence.OccurrenceType type) {
        try {
            Map<?, ?> response = cloudinary.uploader().upload(
                fileBytes,
                ObjectUtils.asMap(
                    "folder",           "fala-cidade/occurrences",
                    "resource_type",    "image",
                    // Detecta se a imagem está borrada (quality_analysis.focus 0-1)
                    "quality_analysis", true
                )
            );

            String  publicId = (String) response.get("public_id");
            boolean blurred  = detectBlur(response);

            // Categoria crítica + foto borrada (fora de foco) → rejeitar e deletar
            if (blurred && isCritical(type)) {
                deleteFromCloudinary(publicId);
                return new UploadResult(null, null, true, true, REJECTION_MESSAGE);
            }

            // Anonimização (LGPD): monta a URL DE ENTREGA com desfoque de rostos E de
            // texto (placas de veículo). As transformações são resolvidas pelo Cloudinary
            // no momento do download — não armazena o original "cru".
            String url = buildPrivacyUrl(publicId);

            return new UploadResult(publicId, url, blurred, false, null);

        } catch (Exception e) {
            throw new RuntimeException("Erro no upload para o Cloudinary: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Extrai o score de foco da resposta do Cloudinary.
     * Estrutura esperada: { "quality_analysis": { "focus": 0.82 } }
     * Se o campo não existir (plano sem quality_analysis), retorna false
     * para não penalizar o cidadão por limitação do plano.
     */
    private boolean detectBlur(Map<?, ?> response) {
        try {
            if (response.get("quality_analysis") instanceof Map<?, ?> qa) {
                if (qa.get("focus") instanceof Number focus) {
                    return focus.doubleValue() < BLUR_THRESHOLD;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Monta a URL de entrega com as transformações de privacidade (LGPD) usando o SDK:
     *   1. e_blur_faces             → desfoca rostos detectados (recurso NATIVO do Cloudinary);
     *   2. e_blur_region,g_ocr_text → desfoca TODO texto detectado, inclui placas (ADD-ON OCR).
     *
     * Ex.: .../image/upload/s--abc123--/e_blur_faces:800/e_blur_region:800,g_ocr_text/foto.jpg
     *
     * POR QUE GERAR PELO SDK (e não concatenar string)?
     *   A doc do Cloudinary exige que URLs que usam ADD-ON sejam ASSINADAS (ou geradas como
     *   eager). URL não-assinada com g_ocr_text é RECUSADA → imagem quebrada. Por isso, quando
     *   blurPlates=true, geramos a URL ASSINADA (signed=true → segmento s--...-- na URL).
     *   O e_blur_faces é nativo e funciona sem assinatura, então com blurPlates=false a URL
     *   não precisa ser assinada.
     *
     * REQUISITOS para o blur de placas (OCR) funcionar:
     *   - Add-on "OCR Text Detection and Extraction" ATIVO (Dashboard → Add-ons; tem free tier).
     *   - Imagem com resolução mínima de 1024x768 (exigência do OCR).
     *   - URL assinada (já tratado aqui) OU habilitar "Allow unsigned add-on transformations"
     *     no Console (Settings → Security) — preferimos assinar, é mais seguro.
     *
     * LIMITAÇÃO: o OCR borra QUALQUER texto (placas de rua, fachadas, números), não só a placa
     * do veículo — não há como isolar apenas a placa. Registrar como decisão de projeto.
     */
    private String buildPrivacyUrl(String publicId) {
        if (publicId == null) return null;
        Transformation<?> transformation = new Transformation<>().effect("blur_faces:800");
        if (blurPlates) {
            transformation.chain().effect("blur_region:800").gravity("ocr_text");
        }
        return cloudinary.url()
            .secure(true)
            .signed(blurPlates)   // add-on (OCR) exige URL assinada; rostos não precisam
            .transformation(transformation)
            .generate(publicId);
    }

    private boolean isCritical(Occurrence.OccurrenceType type) {
        return type != null && CRITICAL_TYPES.contains(type);
    }

    private void deleteFromCloudinary(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {}
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        createdAt.forEach((id, ts) -> {
            if (now - ts > MAX_AGE_MS) {
                statusMap.remove(id);
                createdAt.remove(id);
            }
        });
    }
}

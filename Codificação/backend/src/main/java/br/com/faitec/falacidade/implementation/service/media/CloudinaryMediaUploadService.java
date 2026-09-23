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

@Service
public class CloudinaryMediaUploadService implements MediaUploadService {

    private static final double BLUR_THRESHOLD = 0.3;
    private static final long   MAX_AGE_MS     = 10 * 60 * 1000L;

    private static final Set<Occurrence.OccurrenceType> CRITICAL_TYPES = Set.of(
        Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS,
        Occurrence.OccurrenceType.PESSOA_PRECISANDO_DE_AJUDA
    );

    private static final String REJECTION_MESSAGE =
        "A foto está borrada e não pode ser aceita para este tipo de denúncia. " +
        "Por favor, tire uma nova foto com melhor nitidez.";

    private final Cloudinary cloudinary;

    @Value("${cloudinary.blur-plates:true}")
    private boolean blurPlates;

    private final ConcurrentHashMap<String, UploadStatus> statusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>         createdAt = new ConcurrentHashMap<>();

    public CloudinaryMediaUploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * O @Async precisa ficar no método chamado de fora da classe. Posto no
     * uploadSync, que é chamado aqui dentro, o Spring não passaria pelo proxy
     * e a anotação seria ignorada — auto-invocação não é interceptada por AOP.
     */
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

    @Override
    public UploadResult uploadSync(byte[] fileBytes, Occurrence.OccurrenceType type) {
        try {
            Map<?, ?> response = cloudinary.uploader().upload(
                fileBytes,
                ObjectUtils.asMap(
                    "folder",           "fala-cidade/occurrences",
                    "resource_type",    "image",
                    "quality_analysis", true
                )
            );

            String  publicId = (String) response.get("public_id");
            boolean blurred  = detectBlur(response);

            if (blurred && isCritical(type)) {
                deleteFromCloudinary(publicId);
                return new UploadResult(null, null, true, true, REJECTION_MESSAGE);
            }

            String url = buildPrivacyUrl(publicId);

            return new UploadResult(publicId, url, blurred, false, null);

        } catch (Exception e) {
            throw new RuntimeException("Erro no upload para o Cloudinary: " + e.getMessage(), e);
        }
    }

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

    private String buildPrivacyUrl(String publicId) {
        if (publicId == null) return null;
        Transformation<?> transformation = new Transformation<>().effect("blur_faces:800");
        if (blurPlates) {
            transformation.chain().effect("blur_region:800").gravity("ocr_text");
        }
        // O desfoque de texto depende do add-on de OCR, e o Cloudinary recusa URL
        // não assinada que use add-on — a imagem voltaria quebrada. O desfoque de
        // rostos é nativo e dispensa a assinatura, daí a assinatura acompanhar
        // exatamente blurPlates.
        return cloudinary.url()
            .secure(true)
            .signed(blurPlates)
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

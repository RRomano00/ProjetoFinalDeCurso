package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UploadStatus;
import br.com.faitec.falacidade.implementation.service.media.CloudinaryMediaUploadService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService.UploadResult;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryMediaUploadService")
class CloudinaryMediaUploadServiceTest {

    @Mock Cloudinary cloudinary;
    @Mock Uploader  uploader;

    CloudinaryMediaUploadService sut;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        // buildPrivacyUrl usa cloudinary.url(); devolve um Url real com config fake
        // (cloud_name/api_secret de teste) para gerar e assinar URLs sem rede.
        lenient().when(cloudinary.url()).thenAnswer(inv ->
            new Cloudinary("cloudinary://key:secret@test-cloud").url());
        sut = new CloudinaryMediaUploadService(cloudinary);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Map<String, Object> cloudinaryResponse(String publicId, String url, Double focusScore) {
        Map<String, Object> response = new HashMap<>();
        response.put("public_id",  publicId);
        response.put("secure_url", url);

        if (focusScore != null) {
            Map<String, Object> qa = new HashMap<>();
            qa.put("focus", focusScore);
            response.put("quality_analysis", qa);
        }

        return response;
    }

    // ================================================================
    // uploadSync() – detecção de blur e categorias críticas
    // ================================================================

    @Nested
    @DisplayName("uploadSync()")
    class UploadSync {

        @Test
        @DisplayName("foto nítida em categoria normal → aceita sem rejeição")
        void sharpPhotoNonCritical() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img1", "https://url.jpg", 0.85));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA
            );

            assertThat(result.rejected()).isFalse();
            assertThat(result.blurred()).isFalse();
            // A URL de entrega aplica o blur de rostos (LGPD), não é a secure_url crua
            assertThat(result.url()).contains("e_blur_faces").contains("fc/img1");
            assertThat(result.publicId()).isEqualTo("fc/img1");
        }

        @Test
        @DisplayName("foto borrada (focus=0.15) em categoria normal → aceita com flag blurred=true")
        void blurredPhotoNonCritical() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img2", "https://url2.jpg", 0.15));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.LIXO_ACUMULADO_OU_TERRENO_SUJO
            );

            assertThat(result.blurred()).isTrue();
            assertThat(result.rejected()).isFalse();
            assertThat(result.url()).contains("e_blur_faces").contains("fc/img2");
        }

        @Test
        @DisplayName("foto borrada em MAUS_TRATOS_AOS_ANIMAIS → REJEITADA e deletada do Cloudinary")
        void blurredPhotoCriticalAnimalAbuse() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img3", "https://url3.jpg", 0.10));
            when(uploader.destroy(eq("fc/img3"), any())).thenReturn(Map.of("result", "ok"));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS
            );

            assertThat(result.rejected()).isTrue();
            assertThat(result.blurred()).isTrue();
            assertThat(result.url()).isNull();
            assertThat(result.publicId()).isNull();
            assertThat(result.rejectionReason()).isNotBlank();

            // Deve deletar a imagem rejeitada do Cloudinary
            verify(uploader).destroy(eq("fc/img3"), any());
        }

        @Test
        @DisplayName("foto borrada em PESSOA_PRECISANDO_DE_AJUDA → REJEITADA")
        void blurredPhotoCriticalPersonInNeed() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img4", "https://url4.jpg", 0.05));
            when(uploader.destroy(anyString(), any())).thenReturn(Map.of());

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.PESSOA_PRECISANDO_DE_AJUDA
            );

            assertThat(result.rejected()).isTrue();
        }

        @Test
        @DisplayName("foto nítida em MAUS_TRATOS_AOS_ANIMAIS → aceita normalmente")
        void sharpPhotoCriticalAccepted() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img5", "https://url5.jpg", 0.90));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS
            );

            assertThat(result.rejected()).isFalse();
            assertThat(result.blurred()).isFalse();
            assertThat(result.url()).contains("e_blur_faces").contains("fc/img5");
        }

        @Test
        @DisplayName("Cloudinary sem campo quality_analysis → considera nítida (não penaliza)")
        void noQualityAnalysisField() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img6", "https://url6.jpg", null));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS
            );

            assertThat(result.blurred()).isFalse();
            assertThat(result.rejected()).isFalse();
        }

        @Test
        @DisplayName("focus exatamente em 0.3 (limiar) → considerada nítida (regra: < 0.3 é borrada)")
        void focusAtThreshold() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img7", "https://url7.jpg", 0.3));

            UploadResult result = sut.uploadSync(
                new byte[]{1,2,3},
                Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS
            );

            assertThat(result.blurred()).isFalse();
            assertThat(result.rejected()).isFalse();
        }

        @Test
        @DisplayName("falha no Cloudinary → lança RuntimeException")
        void cloudinaryFailureThrows() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenThrow(new java.io.IOException("timeout"));

            assertThatThrownBy(() ->
                sut.uploadSync(new byte[]{1}, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA)
            ).isInstanceOf(RuntimeException.class)
             .hasMessageContaining("Cloudinary");
        }
    }

    // ================================================================
    // uploadAsync() + getUploadStatus() – fluxo assíncrono
    // ================================================================

    @Nested
    @DisplayName("uploadAsync() + getUploadStatus()")
    class AsyncFlow {

        @Test
        @DisplayName("uploadId desconhecido retorna null")
        void unknownUploadId() {
            assertThat(sut.getUploadStatus("id-inexistente")).isNull();
        }

        @Test
        @DisplayName("status fica PROCESSING enquanto ainda não concluiu")
        void processingState() {
            // Registrar manualmente um status PROCESSING (sem chamar uploadAsync para evitar thread real)
            // Validamos via uploadSync + mocking que o estado correto é propagado
            // O teste do estado PROCESSING é cobertura do domínio UploadStatus
            UploadStatus status = new UploadStatus("test-id");
            assertThat(status.getState()).isEqualTo(UploadStatus.State.PROCESSING);
        }

        @Test
        @DisplayName("status fica DONE após upload bem sucedido")
        void doneStateAfterSuccess() {
            UploadStatus status = new UploadStatus("test-id");
            status.markDone("fc/public-id", "https://url.jpg", false);

            assertThat(status.getState()).isEqualTo(UploadStatus.State.DONE);
            assertThat(status.getPublicId()).isEqualTo("fc/public-id");
            assertThat(status.getUrl()).isEqualTo("https://url.jpg");
            assertThat(status.isBlurred()).isFalse();
        }

        @Test
        @DisplayName("status fica REJECTED após foto borrada em categoria crítica")
        void rejectedState() {
            UploadStatus status = new UploadStatus("test-id");
            status.markRejected("Foto borrada. Tire outra.");

            assertThat(status.getState()).isEqualTo(UploadStatus.State.REJECTED);
            assertThat(status.getMessage()).contains("borrada");
        }

        @Test
        @DisplayName("status fica ERROR após falha no Cloudinary")
        void errorState() {
            UploadStatus status = new UploadStatus("test-id");
            status.markError("Timeout na conexão");

            assertThat(status.getState()).isEqualTo(UploadStatus.State.ERROR);
            assertThat(status.getMessage()).contains("Timeout");
        }

        @Test
        @DisplayName("uploadAsync registra status no mapa e fica consultável")
        void asyncRegistersStatus() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img", "https://img.jpg", 0.9));

            String uploadId = "test-async-id";
            // Executar de forma síncrona para o teste (a anotação @Async é ignorada sem contexto Spring)
            sut.uploadAsync(new byte[]{1, 2, 3}, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA, uploadId);

            UploadStatus status = sut.getUploadStatus(uploadId);
            assertThat(status).isNotNull();
            assertThat(status.getState()).isEqualTo(UploadStatus.State.DONE);
            assertThat(status.getUrl()).contains("e_blur_faces").contains("fc/img");
        }

        @Test
        @DisplayName("uploadAsync em categoria crítica com foto borrada → status REJECTED")
        void asyncRejectedForCritical() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenReturn(cloudinaryResponse("fc/img", "https://img.jpg", 0.05));
            when(uploader.destroy(anyString(), any())).thenReturn(Map.of());

            String uploadId = "test-critical-id";
            sut.uploadAsync(new byte[]{1, 2, 3}, Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS, uploadId);

            UploadStatus status = sut.getUploadStatus(uploadId);
            assertThat(status.getState()).isEqualTo(UploadStatus.State.REJECTED);
        }

        @Test
        @DisplayName("uploadAsync com falha no Cloudinary → status ERROR")
        void asyncErrorOnCloudinaryFailure() throws Exception {
            when(uploader.upload(any(byte[].class), any()))
                .thenThrow(new java.io.IOException("connection refused"));

            String uploadId = "test-error-id";
            sut.uploadAsync(new byte[]{1, 2, 3}, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA, uploadId);

            UploadStatus status = sut.getUploadStatus(uploadId);
            assertThat(status.getState()).isEqualTo(UploadStatus.State.ERROR);
            assertThat(status.getMessage()).isNotBlank();
        }
    }
}

package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UploadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UploadStatus – máquina de estados")
class UploadStatusTest {

    @Test
    @DisplayName("estado inicial é PROCESSING")
    void initialStateIsProcessing() {
        UploadStatus status = new UploadStatus("id-1");
        assertThat(status.getState()).isEqualTo(UploadStatus.State.PROCESSING);
        assertThat(status.getUploadId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("markDone preenche url, publicId, blurred e muda estado para DONE")
    void markDoneTransition() {
        UploadStatus status = new UploadStatus("id-1");
        status.markDone("fc/img", "https://url.jpg", false);

        assertThat(status.getState()).isEqualTo(UploadStatus.State.DONE);
        assertThat(status.getPublicId()).isEqualTo("fc/img");
        assertThat(status.getUrl()).isEqualTo("https://url.jpg");
        assertThat(status.isBlurred()).isFalse();
        assertThat(status.getMessage()).isNull();
    }

    @Test
    @DisplayName("markDone com blurred=true registra corretamente")
    void markDoneWithBlur() {
        UploadStatus status = new UploadStatus("id-1");
        status.markDone("fc/img", "https://url.jpg", true);
        assertThat(status.isBlurred()).isTrue();
    }

    @Test
    @DisplayName("markRejected preenche message e muda estado para REJECTED")
    void markRejectedTransition() {
        UploadStatus status = new UploadStatus("id-1");
        status.markRejected("Foto borrada, tire outra.");

        assertThat(status.getState()).isEqualTo(UploadStatus.State.REJECTED);
        assertThat(status.getMessage()).isEqualTo("Foto borrada, tire outra.");
        assertThat(status.getUrl()).isNull();
        assertThat(status.getPublicId()).isNull();
    }

    @Test
    @DisplayName("markError preenche message e muda estado para ERROR")
    void markErrorTransition() {
        UploadStatus status = new UploadStatus("id-1");
        status.markError("Timeout na conexão com Cloudinary");

        assertThat(status.getState()).isEqualTo(UploadStatus.State.ERROR);
        assertThat(status.getMessage()).contains("Timeout");
    }

    @Test
    @DisplayName("uploadId nunca é null mesmo após transição de estado")
    void uploadIdPreservedAfterTransition() {
        UploadStatus status = new UploadStatus("meu-uuid-fixo");
        status.markDone("p", "u", false);
        assertThat(status.getUploadId()).isEqualTo("meu-uuid-fixo");
    }
}

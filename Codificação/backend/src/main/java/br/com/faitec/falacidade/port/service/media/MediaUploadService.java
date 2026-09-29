package br.com.faitec.falacidade.port.service.media;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UploadStatus;

public interface MediaUploadService {

    void uploadAsync(byte[] fileBytes, Occurrence.OccurrenceType type, String uploadId);

    UploadStatus getUploadStatus(String uploadId);

    UploadResult uploadSync(byte[] fileBytes, Occurrence.OccurrenceType type);

    /** Apaga a imagem no provedor; falha não interrompe quem chamou. */
    void delete(String publicId);

    record UploadResult(
        String  publicId,
        String  url,
        boolean blurred,
        boolean rejected,
        String  rejectionReason
    ) {}
}

package br.com.faitec.falacidade.port.service.media;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UploadStatus;

public interface MediaUploadService {

    /**
     * Inicia o upload assíncrono.
     * Retorna imediatamente — o status é consultado via getUploadStatus().
     *
     * @param fileBytes bytes lidos do MultipartFile antes de liberar o request HTTP
     * @param type      categoria da ocorrência (define se foto borrada é rejeitada)
     * @param uploadId  UUID gerado pelo controller para identificar este upload
     */
    void uploadAsync(byte[] fileBytes, Occurrence.OccurrenceType type, String uploadId);

    /**
     * Consulta o estado de um upload em andamento.
     * Retorna null se o uploadId for desconhecido ou já expirou (> 10 min).
     */
    UploadStatus getUploadStatus(String uploadId);

    /**
     * Upload síncrono — usado internamente pelo uploadAsync e disponível para testes.
     */
    UploadResult uploadSync(byte[] fileBytes, Occurrence.OccurrenceType type);

    record UploadResult(
        String  publicId,
        String  url,
        boolean blurred,
        boolean rejected,
        String  rejectionReason
    ) {}
}

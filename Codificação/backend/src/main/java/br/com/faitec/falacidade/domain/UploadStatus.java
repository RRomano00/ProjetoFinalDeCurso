package br.com.faitec.falacidade.domain;

/**
 * Representa o estado de um upload assíncrono para o Cloudinary.
 *
 * Ciclo de vida:
 *   PROCESSING → DONE (aceito)
 *   PROCESSING → REJECTED (foto borrada em categoria crítica)
 *   PROCESSING → ERROR (falha de rede/Cloudinary)
 */
public class UploadStatus {

    public enum State { PROCESSING, DONE, REJECTED, ERROR }

    private final String uploadId;
    private volatile State state;

    // Preenchidos quando state == DONE
    private volatile String publicId;
    private volatile String url;
    private volatile boolean blurred;

    // Preenchidos quando state == REJECTED ou ERROR
    private volatile String message;

    public UploadStatus(String uploadId) {
        this.uploadId = uploadId;
        this.state    = State.PROCESSING;
    }

    public void markDone(String publicId, String url, boolean blurred) {
        this.publicId = publicId;
        this.url      = url;
        this.blurred  = blurred;
        this.state    = State.DONE;
    }

    public void markRejected(String message) {
        this.message = message;
        this.state   = State.REJECTED;
    }

    public void markError(String message) {
        this.message = message;
        this.state   = State.ERROR;
    }

    // ---- getters (sem setters – estado muda só pelos métodos mark*) ----
    public String getUploadId() { return uploadId; }
    public State  getState()    { return state; }
    public String getPublicId() { return publicId; }
    public String getUrl()      { return url; }
    public boolean isBlurred()  { return blurred; }
    public String getMessage()  { return message; }
}

package br.com.faitec.falacidade.domain;

/** RF07: uma foto anexada à ocorrência (URL de entrega já com blur de privacidade). */
public class OccurrenceMedia {

    private String url;
    private String cloudinaryPublicId;
    private boolean imageBlurred;

    public OccurrenceMedia() {}

    public OccurrenceMedia(String url, String cloudinaryPublicId, boolean imageBlurred) {
        this.url = url;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.imageBlurred = imageBlurred;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }
    public boolean isImageBlurred() { return imageBlurred; }
    public void setImageBlurred(boolean imageBlurred) { this.imageBlurred = imageBlurred; }
}

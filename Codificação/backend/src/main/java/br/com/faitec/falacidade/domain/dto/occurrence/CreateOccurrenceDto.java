package br.com.faitec.falacidade.domain.dto.occurrence;

import br.com.faitec.falacidade.domain.Occurrence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Payload para registrar uma ocorrência — POST /api/occurrence. */
public class CreateOccurrenceDto {

    @NotBlank(message = "Título é obrigatório")
    private String title;

    @NotBlank(message = "Descrição é obrigatória")
    private String description;

    @NotNull(message = "Tipo da ocorrência é obrigatório")
    private Occurrence.OccurrenceType type;

    @NotBlank(message = "Logradouro é obrigatório")
    private String street;

    private String number;

    @NotBlank(message = "Bairro é obrigatório")
    private String neighborhood;

    private String addressReference;

    @NotBlank(message = "Cidade é obrigatória")
    private String city;

    private Double latitude, longitude;
    private String email;
    private String cloudinaryPublicId;
    private String urlMedia;

    /** RF07: fotos anexadas (opcional; a 1ª vira a capa urlMedia). */
    private java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> media;

    public Occurrence toOccurrence() {
        Occurrence o = new Occurrence();
        o.setTitle(title);             o.setDescription(description);
        o.setType(type);               o.setStreet(street);
        o.setNumber(number);           o.setNeighborhood(neighborhood);
        o.setAddressReference(addressReference);
        o.setCity(city);               o.setLatitude(latitude);
        o.setLongitude(longitude);     o.setEmail(email);
        o.setCloudinaryPublicId(cloudinaryPublicId);
        o.setUrlMedia(urlMedia);
        o.setMedia(media);
        // A 1ª foto da lista vira a capa quando urlMedia não veio preenchido
        if (o.getUrlMedia() == null && media != null && !media.isEmpty()) {
            o.setUrlMedia(media.get(0).getUrl());
            o.setCloudinaryPublicId(media.get(0).getCloudinaryPublicId());
        }
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        o.setPriority(Occurrence.Priority.fromType(type));
        o.setAnonymous(email == null || email.isBlank());
        return o;
    }

    public String                getDescription()        { return description; }
    public void                  setDescription(String v){ this.description = v; }
    public String                getTitle()              { return title; }
    public void                  setTitle(String v)      { this.title = v; }
    public Occurrence.OccurrenceType getType()               { return type; }
    public void                  setType(Occurrence.OccurrenceType v){ this.type = v; }
    public String                getStreet()             { return street; }
    public void                  setStreet(String v)     { this.street = v; }
    public String                getNumber()             { return number; }
    public void                  setNumber(String v)     { this.number = v; }
    public String                getNeighborhood()       { return neighborhood; }
    public void                  setNeighborhood(String v){ this.neighborhood = v; }
    public String                getAddressReference()   { return addressReference; }
    public void                  setAddressReference(String v){ this.addressReference = v; }
    public String                getCity()               { return city; }
    public void                  setCity(String v)       { this.city = v; }
    public Double                getLatitude()           { return latitude; }
    public void                  setLatitude(Double v)   { this.latitude = v; }
    public Double                getLongitude()          { return longitude; }
    public void                  setLongitude(Double v)  { this.longitude = v; }
    public String                getEmail()              { return email; }
    public void                  setEmail(String v)      { this.email = v; }
    public String                getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void                  setCloudinaryPublicId(String v){ this.cloudinaryPublicId = v; }
    public String                getUrlMedia()           { return urlMedia; }
    public void                  setUrlMedia(String v)   { this.urlMedia = v; }
    public java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> getMedia() { return media; }
    public void                  setMedia(java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> v) { this.media = v; }
}

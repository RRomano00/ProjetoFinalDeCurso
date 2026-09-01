package br.com.faitec.falacidade.domain.dto.occurrence;

import br.com.faitec.falacidade.domain.Occurrence;
import java.time.LocalDateTime;

/** DTO de leitura de ocorrência — GET /api/occurrence e /api/occurrence/{id}. */
public class GetOccurrenceDto {
    private int                      id;
    private String                   protocolNumber, title, description;
    private String                   neighborhood, number, street, addressReference, city;
    private Double                   latitude, longitude;
    private String                   urlMedia;
    private boolean                  imageBlurred;
    /** RF07: todas as fotos anexadas (carregado só no detalhe). */
    private java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> media;
    private Occurrence.OccurrenceStatus  status;
    private Occurrence.OccurrenceType    type;
    private Occurrence.Priority      priority;
    private boolean                  anonymous;
    /** RF12: id da ocorrência raiz do grupo de duplicatas (null = não agrupada). */
    private Integer                  groupId;
    private String                   email, fullname;
    private LocalDateTime            createdAt, updatedAt;

    public int                     getId()              { return id; }
    public void                    setId(int v)         { this.id = v; }
    public String                  getProtocolNumber()  { return protocolNumber; }
    public void                    setProtocolNumber(String v){ this.protocolNumber = v; }
    public String                  getTitle()           { return title; }
    public void                    setTitle(String v)   { this.title = v; }
    public String                  getDescription()     { return description; }
    public void                    setDescription(String v){ this.description = v; }
    public String                  getNeighborhood()    { return neighborhood; }
    public void                    setNeighborhood(String v){ this.neighborhood = v; }
    public String                  getNumber()          { return number; }
    public void                    setNumber(String v)  { this.number = v; }
    public String                  getStreet()          { return street; }
    public void                    setStreet(String v)  { this.street = v; }
    public String                  getAddressReference(){ return addressReference; }
    public void                    setAddressReference(String v){ this.addressReference = v; }
    public String                  getCity()            { return city; }
    public void                    setCity(String v)    { this.city = v; }
    public Double                  getLatitude()        { return latitude; }
    public void                    setLatitude(Double v){ this.latitude = v; }
    public Double                  getLongitude()       { return longitude; }
    public void                    setLongitude(Double v){ this.longitude = v; }
    public String                  getUrlMedia()        { return urlMedia; }
    public void                    setUrlMedia(String v){ this.urlMedia = v; }
    public boolean                 isImageBlurred()     { return imageBlurred; }
    public void                    setImageBlurred(boolean v){ this.imageBlurred = v; }
    public java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> getMedia() { return media; }
    public void                    setMedia(java.util.List<br.com.faitec.falacidade.domain.OccurrenceMedia> v) { this.media = v; }
    public Occurrence.OccurrenceStatus getStatus()          { return status; }
    public void                    setStatus(Occurrence.OccurrenceStatus v){ this.status = v; }
    public Occurrence.OccurrenceType   getType()            { return type; }
    public void                    setType(Occurrence.OccurrenceType v){ this.type = v; }
    public Occurrence.Priority     getPriority()        { return priority; }
    public void                    setPriority(Occurrence.Priority v){ this.priority = v; }
    public boolean                 isAnonymous()        { return anonymous; }
    public void                    setAnonymous(boolean v){ this.anonymous = v; }
    public Integer                 getGroupId()         { return groupId; }
    public void                    setGroupId(Integer v){ this.groupId = v; }
    public String                  getEmail()           { return email; }
    public void                    setEmail(String v)   { this.email = v; }
    public String                  getFullname()        { return fullname; }
    public void                    setFullname(String v){ this.fullname = v; }
    public LocalDateTime           getCreatedAt()       { return createdAt; }
    public void                    setCreatedAt(LocalDateTime v){ this.createdAt = v; }
    public LocalDateTime           getUpdatedAt()       { return updatedAt; }
    public void                    setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }
}

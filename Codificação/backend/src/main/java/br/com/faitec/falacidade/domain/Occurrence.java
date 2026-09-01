package br.com.faitec.falacidade.domain;

import java.time.LocalDateTime;

public class Occurrence {

    private int id;
    private String protocolNumber;
    private String title;
    private String description;
    private String neighborhood;
    private Double latitude;
    private Double longitude;
    private String number;
    private String street;
    private String city;
    private String urlMedia;
    private String cloudinaryPublicId;
    private boolean imageBlurred;

    /** RF07: fotos anexadas (a 1ª também é gravada em urlMedia como capa). */
    private java.util.List<OccurrenceMedia> media;
    private OccurrenceStatus status;
    private OccurrenceType type;
    private Priority priority;
    private boolean anonymous;
    private String email;
    private int usersId;

    /**
     * Hash SHA-256 (hex, 64 chars) do código de rastreamento anônimo.
     * NUNCA armazenamos o código em plain text — só o hash.
     * NULL para ocorrências identificadas.
     */
    private String anonymousTrackingCodeHash;

    /** Ponto de referência do endereço (ex.: "em frente ao mercado X"). */
    private String addressReference;

    /** RF12: id da ocorrência raiz do grupo de duplicatas (null = não agrupada / é a raiz). */
    private Integer groupId;

    /** IPv4/IPv6 do cliente — armazenado apenas para ocorrências anônimas (RF08). */
    private String ipAddress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum OccurrenceType {
        BURACO_NA_RUA_OU_CALCADA,
        POSTE_COM_LUZ_QUEIMADA,
        LIXO_ACUMULADO_OU_TERRENO_SUJO,
        SINALIZACAO_OU_SEMAFORO_COM_DEFEITO,
        PROBLEMAS_EM_PRACAS_E_PARQUES,
        FALHAS_NO_TRANSPORTE_PUBLICO,
        PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA,
        SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO,
        OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO,
        MAUS_TRATOS_AOS_ANIMAIS,
        PESSOA_PRECISANDO_DE_AJUDA,
        OUTROS_PROBLEMAS
    }

    public enum OccurrenceStatus {
        PENDENTE, EM_ANDAMENTO, ATENDIDA, INDEFERIDA
    }

    public enum Priority {
        BAIXA, MEDIA, ALTA;

        public static Priority fromType(OccurrenceType type) {
            return switch (type) {
                case MAUS_TRATOS_AOS_ANIMAIS,
                     PESSOA_PRECISANDO_DE_AJUDA,
                     SINALIZACAO_OU_SEMAFORO_COM_DEFEITO -> ALTA;
                case PROBLEMAS_EM_PRACAS_E_PARQUES,
                     SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO -> BAIXA;
                default -> MEDIA;
            };
        }
    }

    public Occurrence() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getProtocolNumber() { return protocolNumber; }
    public void setProtocolNumber(String protocolNumber) { this.protocolNumber = protocolNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getUrlMedia() { return urlMedia; }
    public void setUrlMedia(String urlMedia) { this.urlMedia = urlMedia; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }
    public boolean isImageBlurred() { return imageBlurred; }
    public void setImageBlurred(boolean imageBlurred) { this.imageBlurred = imageBlurred; }
    public java.util.List<OccurrenceMedia> getMedia() { return media; }
    public void setMedia(java.util.List<OccurrenceMedia> media) { this.media = media; }
    public OccurrenceStatus getStatus() { return status; }
    public void setStatus(OccurrenceStatus status) { this.status = status; }
    public OccurrenceType getType() { return type; }
    public void setType(OccurrenceType type) { this.type = type; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getUsersId() { return usersId; }
    public void setUsersId(int usersId) { this.usersId = usersId; }
    public String getAnonymousTrackingCodeHash() { return anonymousTrackingCodeHash; }
    public void setAnonymousTrackingCodeHash(String hash) { this.anonymousTrackingCodeHash = hash; }
    public String getAddressReference() { return addressReference; }
    public void setAddressReference(String addressReference) { this.addressReference = addressReference; }
    public Integer getGroupId() { return groupId; }
    public void setGroupId(Integer groupId) { this.groupId = groupId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package br.com.faitec.falacidade.domain.dto.occurrence;

public class CreateOccurrenceResponseDto {
    private int     occurrenceId;
    private String  protocolNumber;
    private String  trackingCode;
    private boolean anonymous;

    public CreateOccurrenceResponseDto(int id, String protocol,
                                        String trackingCode, boolean anonymous) {
        this.occurrenceId   = id;
        this.protocolNumber = protocol;
        this.trackingCode   = trackingCode;
        this.anonymous      = anonymous;
    }

    public int     getOccurrenceId()   { return occurrenceId; }
    public String  getProtocolNumber() { return protocolNumber; }
    public String  getTrackingCode()   { return trackingCode; }
    public boolean isAnonymous()       { return anonymous; }
}

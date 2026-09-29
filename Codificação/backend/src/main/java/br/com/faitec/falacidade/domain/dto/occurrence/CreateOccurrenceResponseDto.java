package br.com.faitec.falacidade.domain.dto.occurrence;

public class CreateOccurrenceResponseDto {
    private int     occurrenceId;
    private String  protocolNumber;
    private String  trackingCode;
    private boolean anonymous;
    // Protocolo da ocorrência à qual a nova foi agrupada (mesmo tipo, a até 50 m); null se ficou sozinha.
    private String  groupedWithProtocol;

    public CreateOccurrenceResponseDto(int id, String protocol,
                                        String trackingCode, boolean anonymous) {
        this.occurrenceId   = id;
        this.protocolNumber = protocol;
        this.trackingCode   = trackingCode;
        this.anonymous      = anonymous;
    }

    public CreateOccurrenceResponseDto(int id, String protocol, String trackingCode,
                                        boolean anonymous, String groupedWithProtocol) {
        this(id, protocol, trackingCode, anonymous);
        this.groupedWithProtocol = groupedWithProtocol;
    }

    public int     getOccurrenceId()   { return occurrenceId; }
    public String  getProtocolNumber() { return protocolNumber; }
    public String  getTrackingCode()   { return trackingCode; }
    public boolean isAnonymous()       { return anonymous; }
    public String  getGroupedWithProtocol() { return groupedWithProtocol; }
}

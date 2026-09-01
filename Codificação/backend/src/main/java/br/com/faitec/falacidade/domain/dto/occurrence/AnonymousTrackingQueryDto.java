package br.com.faitec.falacidade.domain.dto.occurrence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload para consulta de ocorrência anônima — GET /api/occurrence/anonymous-status. */
public class AnonymousTrackingQueryDto {
    @NotBlank
    @Size(min = 8, max = 8, message = "Código deve ter exatamente 8 caracteres")
    @Pattern(regexp = "[A-Z2-9]{8}", message = "Código inválido")
    private String trackingCode;

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String v) {
        this.trackingCode = v == null ? null : v.toUpperCase().trim();
    }
}

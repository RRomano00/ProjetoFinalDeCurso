package br.com.faitec.falacidade.domain.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload do formulário de contato — POST /api/contact. */
public class ContactMessageDto {
    @NotBlank private String name;
    @Email @NotBlank private String email;
    private String subject;
    @NotBlank private String message;

    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }
    public String getEmail()         { return email; }
    public void   setEmail(String v) { this.email = v; }
    public String getSubject()       { return subject; }
    public void   setSubject(String v){ this.subject = v; }
    public String getMessage()       { return message; }
    public void   setMessage(String v){ this.message = v; }
}

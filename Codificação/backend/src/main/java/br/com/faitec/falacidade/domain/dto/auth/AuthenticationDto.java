package br.com.faitec.falacidade.domain.dto.auth;

/** Payload do Step 1 do login: email + senha. */
public class AuthenticationDto {
    private String email;
    private String password;

    public String getEmail()    { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

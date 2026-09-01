package br.com.faitec.falacidade.domain.dto.user;

import br.com.faitec.falacidade.domain.UserModel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Payload do cadastro público de cidadão — POST /api/user/register. */
public class RegisterUserDto {

    @NotBlank(message = "Nome é obrigatório")
    private String fullname;

    @Email @NotBlank(message = "E-mail é obrigatório")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    private String password;

    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String street;
    private String neighborhood;
    private String number;
    private String cep;
    private String city;

    @NotNull(message = "Você deve aceitar os termos de uso")
    private Boolean acceptsTerms;

    /** Se true, ativa o MFA por e-mail já no cadastro (opcional). */
    private Boolean mfaEmailEnabled;

    public UserModel toUserModel() {
        UserModel u = new UserModel();
        u.setFullname(fullname);     u.setEmail(email);
        u.setPassword(password);     u.setDateOfBirth(dateOfBirth);
        u.setPhoneNumber(phoneNumber); u.setStreet(street);
        u.setNeighborhood(neighborhood); u.setNumber(number);
        u.setCep(cep);               u.setCity(city);
        u.setAcceptsTerms(Boolean.TRUE.equals(acceptsTerms));
        u.setMfaEmailEnabled(Boolean.TRUE.equals(mfaEmailEnabled));
        u.setRole(UserModel.UserRole.CITIZEN);
        u.setActive(true);
        return u;
    }

    public String    getFullname()     { return fullname; }
    public void      setFullname(String v)     { this.fullname = v; }
    public String    getEmail()        { return email; }
    public void      setEmail(String v)        { this.email = v; }
    public String    getPassword()     { return password; }
    public void      setPassword(String v)     { this.password = v; }
    public LocalDate getDateOfBirth()  { return dateOfBirth; }
    public void      setDateOfBirth(LocalDate v)  { this.dateOfBirth = v; }
    public String    getPhoneNumber()  { return phoneNumber; }
    public void      setPhoneNumber(String v)  { this.phoneNumber = v; }
    public String    getStreet()       { return street; }
    public void      setStreet(String v)       { this.street = v; }
    public String    getNeighborhood() { return neighborhood; }
    public void      setNeighborhood(String v) { this.neighborhood = v; }
    public String    getNumber()       { return number; }
    public void      setNumber(String v)       { this.number = v; }
    public String    getCep()          { return cep; }
    public void      setCep(String v)          { this.cep = v; }
    public String    getCity()         { return city; }
    public void      setCity(String v)         { this.city = v; }
    public Boolean   getAcceptsTerms() { return acceptsTerms; }
    public void      setAcceptsTerms(Boolean v){ this.acceptsTerms = v; }
    public Boolean   getMfaEmailEnabled() { return mfaEmailEnabled; }
    public void      setMfaEmailEnabled(Boolean v){ this.mfaEmailEnabled = v; }
}

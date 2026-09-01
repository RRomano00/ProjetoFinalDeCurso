package br.com.faitec.falacidade.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserModel {

    private int id;
    private String email;
    private String password;
    private String fullname;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String street;
    private String neighborhood;
    private String number;
    private String cep;
    private String city;
    private UserRole role;
    private boolean active;
    private boolean acceptsTerms;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 2FA / TOTP (app autenticador) ----
    private boolean mfaEnabled;
    private String mfaSecret;
    private boolean mfaSetupDone;

    // ---- 2FA por e-mail (código enviado ao e-mail cadastrado) ----
    private boolean mfaEmailEnabled;

    public UserModel() {}

    public enum UserRole {
        ADMINISTRATOR,
        EMPLOYEE,
        CITIZEN
    }

    // ---- getters/setters ----

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isAcceptsTerms() { return acceptsTerms; }
    public void setAcceptsTerms(boolean acceptsTerms) { this.acceptsTerms = acceptsTerms; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    public boolean isMfaSetupDone() { return mfaSetupDone; }
    public void setMfaSetupDone(boolean mfaSetupDone) { this.mfaSetupDone = mfaSetupDone; }

    public boolean isMfaEmailEnabled() { return mfaEmailEnabled; }
    public void setMfaEmailEnabled(boolean mfaEmailEnabled) { this.mfaEmailEnabled = mfaEmailEnabled; }

    /** MFA por app autenticador ativo (secret confirmado). */
    public boolean isAppMfaActive() { return mfaSetupDone; }
    /** MFA por e-mail ativo. */
    public boolean isEmailMfaActive() { return mfaEmailEnabled; }

    /**
     * Retorna true se este usuário PRECISA passar pelo 2FA.
     * EMPLOYEE e ADMINISTRATOR: obrigatório sempre.
     * CITIZEN: só se tiver ativado voluntariamente.
     */
    public boolean requiresMfa() {
        if (role == UserRole.EMPLOYEE || role == UserRole.ADMINISTRATOR) {
            return mfaSetupDone || mfaEmailEnabled; // obrigado a configurar pelo menos um método
        }
        return (mfaEnabled && mfaSetupDone) || mfaEmailEnabled;
    }
}

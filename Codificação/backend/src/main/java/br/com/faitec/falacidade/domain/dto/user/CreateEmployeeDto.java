package br.com.faitec.falacidade.domain.dto.user;

import br.com.faitec.falacidade.domain.UserModel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload para criar EMPLOYEE ou ADMINISTRATOR — POST /api/user/employee.
 * Apenas ADMINISTRATOR pode chamar esse endpoint.
 * No primeiro login, o sistema exige configuração obrigatória do 2FA.
 */
public class CreateEmployeeDto {

    @NotBlank private String fullname;
    @Email @NotBlank private String email;
    @NotBlank private String password;

    /** Município ao qual o funcionário/administrador fica vinculado (b.2). */
    @NotBlank(message = "Município é obrigatório")
    private String city;

    @NotNull(message = "Role é obrigatório: EMPLOYEE ou ADMINISTRATOR")
    private UserModel.UserRole role;

    public UserModel toUserModel() {
        if (role == UserModel.UserRole.CITIZEN)
            throw new IllegalArgumentException("Use /api/user/register para criar cidadãos.");
        UserModel u = new UserModel();
        u.setFullname(fullname); u.setEmail(email); u.setPassword(password);
        u.setCity(city);
        u.setRole(role);         u.setActive(true); u.setAcceptsTerms(true);
        u.setMfaEnabled(false);  u.setMfaSetupDone(false);
        return u;
    }

    public String             getFullname() { return fullname; }
    public void               setFullname(String v) { this.fullname = v; }
    public String             getEmail()    { return email; }
    public void               setEmail(String v)    { this.email = v; }
    public String             getPassword() { return password; }
    public void               setPassword(String v) { this.password = v; }
    public String             getCity()     { return city; }
    public void               setCity(String v)     { this.city = v; }
    public UserModel.UserRole getRole()     { return role; }
    public void               setRole(UserModel.UserRole v) { this.role = v; }
}

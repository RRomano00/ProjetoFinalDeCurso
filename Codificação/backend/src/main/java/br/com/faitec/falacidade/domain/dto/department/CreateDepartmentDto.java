package br.com.faitec.falacidade.domain.dto.department;

import br.com.faitec.falacidade.domain.Department;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDepartmentDto {

    @NotBlank(message = "Nome do departamento é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String name;

    @NotBlank(message = "E-mail do departamento é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 150, message = "E-mail deve ter no máximo 150 caracteres")
    private String email;

    @NotBlank(message = "Município do departamento é obrigatório")
    @Size(max = 100, message = "Município deve ter no máximo 100 caracteres")
    private String city;

    @NotBlank(message = "UF do departamento é obrigatória")
    @Size(min = 2, max = 2, message = "UF deve ter duas letras")
    private String state;

    public Department toDepartment() {
        Department d = new Department();
        d.setName(name);
        d.setEmail(email);
        d.setCity(city);
        d.setState(state);
        return d;
    }

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }
    public String getEmail()             { return email; }
    public void   setEmail(String email) { this.email = email; }
    public String getCity()              { return city; }
    public void   setCity(String city)   { this.city = city; }
    public String getState()             { return state; }
    public void   setState(String state) { this.state = state; }
}

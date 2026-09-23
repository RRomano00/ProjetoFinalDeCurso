package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.DepartmentRestController;
import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * RF22/RF25: o setor é municipal. A equipe só enxerga os do seu município,
 * mesmo informando outro na consulta — a ocorrência que ela encaminha é sempre
 * do seu município (RN07), então o filtro só serviria para espiar a prefeitura
 * vizinha. O Super Administrador, sem município próprio, alcança todos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alcance da listagem de departamentos")
class DepartmentListingScopeTest {

    @Mock DepartmentService departmentService;
    @Mock UserService       userService;

    DepartmentRestController sut;

    @BeforeEach
    void setUp() { sut = new DepartmentRestController(departmentService, userService); }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTE")));
    }

    private UserModel user(String email, UserModel.UserRole role, String city, String state) {
        UserModel u = new UserModel();
        u.setId(5); u.setEmail(email); u.setRole(role); u.setCity(city); u.setState(state);
        return u;
    }

    @Test
    @DisplayName("funcionário que pede outro município recebe os do seu")
    void staffCannotPeekAnotherCity() {
        UserModel eu = user("func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(eu.getEmail())).thenReturn(eu);
        when(departmentService.findAllByCity("Itajubá", "MG")).thenReturn(List.of(new Department()));

        ResponseEntity<List<Department>> res = sut.getAll("Pouso Alegre", "MG", auth(eu.getEmail()));

        assertThat(res.getBody()).hasSize(1);
        verify(departmentService).findAllByCity("Itajubá", "MG");
        verify(departmentService, never()).findAllByCity("Pouso Alegre", "MG");
    }

    @Test
    @DisplayName("administrador municipal também fica no seu município")
    void municipalAdminIsAlsoScoped() {
        UserModel adm = user("adm@prefeitura.com", UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG");
        when(userService.findByEmail(adm.getEmail())).thenReturn(adm);

        sut.getAll("Pouso Alegre", "MG", auth(adm.getEmail()));

        verify(departmentService).findAllByCity("Itajubá", "MG");
        verify(departmentService, never()).findAll();
    }

    @Test
    @DisplayName("super administrador filtra por qualquer município")
    void superAdminFiltersAnyCity() {
        UserModel sa = user("admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN, null, null);
        when(userService.findByEmail(sa.getEmail())).thenReturn(sa);

        sut.getAll("Pouso Alegre", "MG", auth(sa.getEmail()));

        verify(departmentService).findAllByCity("Pouso Alegre", "MG");
    }

    @Test
    @DisplayName("super administrador sem filtro vê todos")
    void superAdminSeesEverything() {
        UserModel sa = user("admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN, null, null);
        when(userService.findByEmail(sa.getEmail())).thenReturn(sa);

        sut.getAll(null, null, auth(sa.getEmail()));

        verify(departmentService).findAll();
    }

    @Test
    @DisplayName("sem sessão, nenhum setor")
    void anonymousGetsNothing() {
        assertThat(sut.getAll("Itajubá", "MG", null).getBody()).isEmpty();
        verifyNoInteractions(departmentService);
    }
}

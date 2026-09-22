package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.DepartmentRestController;
import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.department.CreateDepartmentDto;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * RF22/RF25: o administrador municipal edita os setores do seu município — e
 * nem alcança os de outro, nem manda o seu para fora do seu alcance.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alcance da edição de departamentos")
class DepartmentUpdateScopeTest {

    @Mock DepartmentService departmentService;
    @Mock UserService       userService;

    DepartmentRestController sut;

    @BeforeEach
    void setUp() { sut = new DepartmentRestController(departmentService, userService); }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTE")));
    }

    private UserModel adminSantaRita() {
        UserModel u = new UserModel();
        u.setId(2); u.setEmail("admin.sr@falacidade.com");
        u.setRole(UserModel.UserRole.ADMINISTRATOR);
        u.setCity("Santa Rita do Sapucaí"); u.setState("MG");
        return u;
    }

    private Department setor(String city, String state) {
        return new Department(4, "Secretaria de Obras", "obras@prefeitura.br", city, state);
    }

    private CreateDepartmentDto dto(String city, String state) {
        CreateDepartmentDto d = new CreateDepartmentDto();
        d.setName("Secretaria de Obras"); d.setEmail("obras@prefeitura.br");
        d.setCity(city); d.setState(state);
        return d;
    }

    @Test
    @DisplayName("edita o setor do próprio município")
    void editsItsOwn() {
        when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
        when(departmentService.findById(4)).thenReturn(setor("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> r = sut.update(4, dto("santa rita do sapucaí", "mg"),
                                        auth("admin.sr@falacidade.com"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(departmentService).update(eq(4), any());
    }

    @Test
    @DisplayName("não edita o setor de outro município")
    void cannotEditOutsider() {
        when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
        when(departmentService.findById(4)).thenReturn(setor("Itajubá", "MG"));

        ResponseEntity<?> r = sut.update(4, dto("Itajubá", "MG"), auth("admin.sr@falacidade.com"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(departmentService, never()).update(anyInt(), any());
    }

    @Test
    @DisplayName("não move o próprio setor para outro município")
    void cannotMoveOutOfItsCity() {
        when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
        when(departmentService.findById(4)).thenReturn(setor("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> r = sut.update(4, dto("Itajubá", "MG"), auth("admin.sr@falacidade.com"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(departmentService, never()).update(anyInt(), any());
    }

    @Test
    @DisplayName("setor inexistente devolve 404")
    void notFound() {
        when(departmentService.findById(99)).thenReturn(null);

        ResponseEntity<?> r = sut.update(99, dto("Itajubá", "MG"), auth("admin.sr@falacidade.com"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

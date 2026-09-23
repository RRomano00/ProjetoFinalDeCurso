package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.OccurrenceRestController;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.ForwardOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.UpdateOccurrenceStatusDto;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Jurisdição municipal nas ações da equipe")
class OccurrenceJurisdictionTest {

    @Mock OccurrenceService  occurrenceService;
    @Mock MediaUploadService mediaUploadService;
    @Mock UserService        userService;
    @Mock EmailService       emailService;

    OccurrenceRestController sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceRestController(occurrenceService, mediaUploadService, userService, emailService);
    }

    private Authentication auth(String email, UserModel.UserRole role) {
        return new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private UserModel staff(String city, String state) {
        UserModel u = new UserModel();
        u.setId(2);
        u.setEmail("carlos@prefeitura.com");
        u.setRole(UserModel.UserRole.EMPLOYEE);
        u.setCity(city);
        u.setState(state);
        return u;
    }

    private GetOccurrenceDto occurrence(String city, String state) {
        GetOccurrenceDto o = new GetOccurrenceDto();
        o.setId(10);
        o.setProtocolNumber("FC-20260919-B38A4");
        o.setCity(city);
        o.setState(state);
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        return o;
    }

    private UpdateOccurrenceStatusDto statusDto() {
        UpdateOccurrenceStatusDto d = new UpdateOccurrenceStatusDto();
        d.setNewStatus(Occurrence.OccurrenceStatus.CONCLUIDA);
        return d;
    }

    @Test
    @DisplayName("funcionário de outro município não muda o status: 403 e nada é alterado")
    void employeeCannotChangeStatusOfAnotherCity() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Itajubá", "MG"));
        when(userService.findByEmail("carlos@prefeitura.com"))
            .thenReturn(staff("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().toString()).contains("Itajubá");
        verify(occurrenceService, never()).changeStatus(anyInt(), anyString(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("funcionário do mesmo município muda o status normalmente")
    void employeeChangesStatusOfOwnCity() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Santa Rita do Sapucaí", "MG"));
        when(userService.findByEmail("carlos@prefeitura.com"))
            .thenReturn(staff("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(occurrenceService).changeStatus(eq(10), eq("CONCLUIDA"), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("mesma cidade em UFs diferentes não é o mesmo município")
    void sameCityNameInAnotherStateIsRejected() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Bom Jesus", "PI"));
        when(userService.findByEmail("carlos@prefeitura.com")).thenReturn(staff("Bom Jesus", "MG"));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(occurrenceService, never()).changeStatus(anyInt(), anyString(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("super administrador atua em qualquer município")
    void superAdminHasNoJurisdictionBoundary() {
        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(occurrenceService).changeStatus(eq(10), eq("CONCLUIDA"), anyInt(), any(), anyBoolean());
        verify(occurrenceService, never()).findById(anyInt());
    }

    @Test
    @DisplayName("administrador municipal não atua fora do seu município")
    void municipalAdminIsBoundToItsCity() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Itajubá", "MG"));
        when(userService.findByEmail("admin.sr@falacidade.com"))
            .thenReturn(staff("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("admin.sr@falacidade.com", UserModel.UserRole.ADMINISTRATOR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(occurrenceService, never()).changeStatus(anyInt(), anyString(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("encaminhamento também respeita a jurisdição")
    void forwardRespectsJurisdiction() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Itajubá", "MG"));
        when(userService.findByEmail("carlos@prefeitura.com"))
            .thenReturn(staff("Santa Rita do Sapucaí", "MG"));

        ForwardOccurrenceDto dto = new ForwardOccurrenceDto();
        dto.setDepartmentIds(java.util.List.of(3));

        ResponseEntity<?> response = sut.forward(10, dto,
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(occurrenceService, never()).forwardToDepartment(anyInt(), any(), anyInt());
    }

    @Test
    @DisplayName("conta sem município não atende ninguém")
    void staffWithoutCityIsRejected() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Itajubá", "MG"));
        when(userService.findByEmail("carlos@prefeitura.com")).thenReturn(staff(null, null));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().toString()).contains("município");
    }

    @Test
    @DisplayName("ocorrência anterior à municipalização (sem UF) continua sendo tratada")
    void legacyOccurrenceWithoutStateStillWorks() {
        when(occurrenceService.findById(10)).thenReturn(occurrence("Santa Rita do Sapucaí", null));
        when(userService.findByEmail("carlos@prefeitura.com"))
            .thenReturn(staff("Santa Rita do Sapucaí", "MG"));

        ResponseEntity<?> response = sut.updateStatus(10, statusDto(),
            auth("carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}

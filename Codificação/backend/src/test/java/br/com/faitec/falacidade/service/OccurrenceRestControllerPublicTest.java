package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.OccurrenceRestController;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceResponseDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Endpoints que o visitante (sem token) alcança: autoria da ocorrência e limites do upload. */
@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceRestController – endpoints públicos")
class OccurrenceRestControllerPublicTest {

    @Mock OccurrenceService  occurrenceService;
    @Mock MediaUploadService mediaUploadService;
    @Mock UserService        userService;
    @Mock EmailService       emailService;

    OccurrenceRestController sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceRestController(occurrenceService, mediaUploadService, userService, emailService);
    }

    private CreateOccurrenceDto dto(String email) {
        CreateOccurrenceDto d = new CreateOccurrenceDto();
        d.setDescription("Buraco na rua");
        d.setCity("Santa Rita do Sapucaí");
        d.setType(Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA);
        d.setEmail(email);
        return d;
    }

    /** Chama o create() e devolve a ocorrência que chegou ao service. */
    private Occurrence created(CreateOccurrenceDto d, Authentication auth) {
        when(occurrenceService.createOccurrence(any(), any()))
            .thenReturn(new CreateOccurrenceResponseDto(1, "2026-0001", null, false));
        sut.create(d, new MockHttpServletRequest(), auth);
        ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
        verify(occurrenceService).createOccurrence(captor.capture(), any());
        return captor.getValue();
    }

    @Nested
    @DisplayName("POST /api/occurrence – autoria vem do token")
    class Authorship {

        @Test
        @DisplayName("sem token mas pedindo identificação: 401, nada é registrado em nome de terceiros")
        void rejectsIdentifiedRequestWithoutToken() {
            ResponseEntity<?> res = sut.create(dto("vitima@email.com"), new MockHttpServletRequest(), null);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(occurrenceService);
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("sem token e sem e-mail: visitante registra normalmente como anônima")
        void visitorRegistersAnonymously() {
            Occurrence o = created(dto(null), null);
            assertThat(o.getEmail()).isNull();
            assertThat(o.isAnonymous()).isTrue();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("com token: autor é o e-mail do token, não o do corpo")
        void authorComesFromToken() {
            Occurrence o = created(dto("vitima@email.com"),
                new UsernamePasswordAuthenticationToken("cidadao@email.com", null));
            assertThat(o.getEmail()).isEqualTo("cidadao@email.com");
            assertThat(o.isAnonymous()).isFalse();
        }

        @Test
        @DisplayName("com token e corpo sem e-mail: usuário logado ainda pode registrar anônima")
        void loggedUserMayStayAnonymous() {
            Occurrence o = created(dto(null),
                new UsernamePasswordAuthenticationToken("cidadao@email.com", null));
            assertThat(o.getEmail()).isNull();
            assertThat(o.isAnonymous()).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /api/occurrence – limite diário")
    class DailyLimit {

        @Test
        @DisplayName("limite estourado: 429 dizendo qual limite e quando é renovado")
        void tellsWhichLimitAndWhenItResets() {
            when(occurrenceService.createOccurrence(any(), any()))
                .thenThrow(new IllegalStateException("Limite de 5 ocorrências por dia atingido"));

            ResponseEntity<?> res = sut.create(dto("cidadao@email.com"), new MockHttpServletRequest(),
                new UsernamePasswordAuthenticationToken("cidadao@email.com", null));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(((Map<?, ?>) res.getBody()).get("error").toString())
                .isEqualTo("Limite de 5 ocorrências por dia atingido. "
                         + "Você poderá registrar novamente à meia-noite (00:00).");
        }
    }

    @Nested
    @DisplayName("POST /api/occurrence/upload-media – limites do endpoint público")
    class UploadLimits {

        private ResponseEntity<Map<String, String>> upload(MockMultipartFile file) {
            return sut.uploadMedia(file, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA,
                                   new MockHttpServletRequest());
        }

        private MockMultipartFile image() {
            return new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
        }

        @Test
        @DisplayName("arquivo que não é imagem é recusado antes de subir ao Cloudinary")
        void rejectsNonImage() {
            ResponseEntity<Map<String, String>> res =
                upload(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            verifyNoInteractions(mediaUploadService);
        }

        @Test
        @DisplayName("21º envio do mesmo IP no dia recebe 429")
        void limitsUploadsPerIp() {
            for (int i = 0; i < 20; i++)
                assertThat(upload(image()).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(upload(image()).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(mediaUploadService, times(20)).uploadAsync(any(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/occurrence – escopo da listagem")
    class ListingScope {

        private GetOccurrenceDto occurrence(int id, String authorEmail) {
            GetOccurrenceDto o = new GetOccurrenceDto();
            o.setId(id);
            o.setEmail(authorEmail);
            o.setFullname("Autor " + id);
            return o;
        }

        private Authentication authAs(String email, UserModel.UserRole role) {
            return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        }

        private List<GetOccurrenceDto> listAs(Authentication auth) {
            return sut.getAll(auth).getBody();
        }

        @Test
        @DisplayName("cidadão vê TODAS as ocorrências, não apenas as próprias")
        void citizenSeesEveryOccurrence() {
            when(occurrenceService.findAll()).thenReturn(List.of(
                occurrence(1, "joao@email.com"), occurrence(2, "maria@email.com")));

            assertThat(listAs(authAs("joao@email.com", UserModel.UserRole.CITIZEN))).hasSize(2);
            verify(occurrenceService, never()).findAllByUserEmail(anyString());
        }

        @Test
        @DisplayName("cidadão não recebe os dados de quem criou as ocorrências de terceiros")
        void citizenDoesNotSeeOtherAuthors() {
            when(occurrenceService.findAll()).thenReturn(List.of(occurrence(2, "maria@email.com")));

            GetOccurrenceDto other = listAs(authAs("joao@email.com", UserModel.UserRole.CITIZEN)).get(0);
            assertThat(other.getEmail()).isNull();
            assertThat(other.getFullname()).isNull();
        }

        @Test
        @DisplayName("cidadão continua vendo os próprios dados na própria ocorrência (RN05)")
        void citizenStillSeesOwnData() {
            when(occurrenceService.findAll()).thenReturn(List.of(occurrence(1, "joao@email.com")));

            GetOccurrenceDto mine = listAs(authAs("joao@email.com", UserModel.UserRole.CITIZEN)).get(0);
            assertThat(mine.getEmail()).isEqualTo("joao@email.com");
        }

        @Test
        @DisplayName("visitante sem token vê todas, sempre com o autor oculto")
        void visitorSeesAllMasked() {
            when(occurrenceService.findAll()).thenReturn(List.of(
                occurrence(1, "joao@email.com"), occurrence(2, "maria@email.com")));

            assertThat(listAs(null))
                .hasSize(2)
                .allSatisfy(o -> {
                    assertThat(o.getEmail()).isNull();
                    assertThat(o.getFullname()).isNull();
                });
        }

        @Test
        @DisplayName("administrador vê todas COM os dados do autor")
        void administratorKeepsAuthorData() {
            when(occurrenceService.findAll()).thenReturn(List.of(occurrence(1, "joao@email.com")));

            GetOccurrenceDto o = listAs(authAs("admin@email.com", UserModel.UserRole.ADMINISTRATOR)).get(0);
            assertThat(o.getEmail()).isEqualTo("joao@email.com");
            assertThat(o.getFullname()).isNotNull();
        }

        @Test
        @DisplayName("cidadão não vê ocorrências anônimas na listagem")
        void citizenDoesNotSeeAnonymous() {
            GetOccurrenceDto anonima = occurrence(3, null);
            anonima.setAnonymous(true);
            when(occurrenceService.findAll())
                .thenReturn(List.of(occurrence(1, "joao@email.com"), anonima));

            assertThat(listAs(authAs("joao@email.com", UserModel.UserRole.CITIZEN)))
                .extracting(GetOccurrenceDto::getId).containsExactly(1);
        }

        @Test
        @DisplayName("visitante também não vê ocorrências anônimas na listagem")
        void visitorDoesNotSeeAnonymous() {
            GetOccurrenceDto anonima = occurrence(3, null);
            anonima.setAnonymous(true);
            when(occurrenceService.findAll())
                .thenReturn(List.of(occurrence(1, "joao@email.com"), anonima));

            assertThat(listAs(null)).extracting(GetOccurrenceDto::getId).containsExactly(1);
        }

        @Test
        @DisplayName("administrador continua vendo as anônimas")
        void administratorStillSeesAnonymous() {
            GetOccurrenceDto anonima = occurrence(3, null);
            anonima.setAnonymous(true);
            when(occurrenceService.findAll()).thenReturn(List.of(anonima));

            assertThat(listAs(authAs("admin@email.com", UserModel.UserRole.ADMINISTRATOR))).hasSize(1);
        }

        @Test
        @DisplayName("funcionário vê apenas as ocorrências do município vinculado")
        void employeeStaysScopedToCity() {
            UserModel employee = new UserModel();
            employee.setCity("Santa Rita do Sapucaí");
            when(userService.findByEmail("func@email.com")).thenReturn(employee);
            when(occurrenceService.findAllByCity("Santa Rita do Sapucaí"))
                .thenReturn(List.of(occurrence(1, "joao@email.com")));

            assertThat(listAs(authAs("func@email.com", UserModel.UserRole.EMPLOYEE))).hasSize(1);
            verify(occurrenceService, never()).findAll();
        }
    }

    @Nested
    @DisplayName("GET /api/occurrence/support/mine")
    class MySupports {

        @Test
        @DisplayName("sem token devolve lista vazia, sem consultar apoios")
        void anonymousGetsEmptyList() {
            assertThat(sut.mySupports(null).getBody()).isEmpty();
            verify(occurrenceService, never()).getSupportedOccurrenceIds(anyInt());
        }

        @Test
        @DisplayName("a rota /support/mine não é capturada pelo mapeamento /{id}")
        void routeIsNotShadowedByIdMapping() throws Exception {
            org.springframework.test.web.servlet.MockMvc mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(sut).build();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/occurrence/support/mine"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));

            verifyNoInteractions(occurrenceService);
        }

        @Test
        @DisplayName("com token devolve os ids apoiados pelo usuário")
        void authenticatedGetsSupportedIds() {
            UserModel user = new UserModel();
            user.setId(7);
            when(userService.findByEmail("joao@email.com")).thenReturn(user);
            when(occurrenceService.getSupportedOccurrenceIds(7)).thenReturn(List.of(3, 9));

            Authentication auth = new UsernamePasswordAuthenticationToken("joao@email.com", null, List.of());
            assertThat(sut.mySupports(auth).getBody()).containsExactly(3, 9);
        }
    }
}

package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.OccurrenceRestController;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceResponseDto;
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
        @DisplayName("sem token: e-mail do corpo é ignorado e a ocorrência vira anônima")
        void ignoresBodyEmailWhenNotAuthenticated() {
            Occurrence o = created(dto("vitima@email.com"), null);
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
}

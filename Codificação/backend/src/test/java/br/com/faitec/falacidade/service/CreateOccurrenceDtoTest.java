package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CreateOccurrenceDto – toOccurrence()")
class CreateOccurrenceDtoTest {

    private CreateOccurrenceDto dto(String email, Occurrence.OccurrenceType type) {
        CreateOccurrenceDto d = new CreateOccurrenceDto();
        d.setDescription("Descrição teste");
        d.setCity("Franca");
        d.setType(type);
        d.setEmail(email);
        return d;
    }

    @Nested
    @DisplayName("Anonimato (RF08)")
    class Anonymous {

        @Test
        @DisplayName("email null → ocorrência anônima")
        void nullEmailIsAnonymous() {
            Occurrence o = dto(null, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA).toOccurrence();
            assertThat(o.isAnonymous()).isTrue();
        }

        @Test
        @DisplayName("email em branco → ocorrência anônima")
        void blankEmailIsAnonymous() {
            Occurrence o = dto("  ", Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA).toOccurrence();
            assertThat(o.isAnonymous()).isTrue();
        }

        @Test
        @DisplayName("email preenchido → ocorrência identificada")
        void filledEmailIsIdentified() {
            Occurrence o = dto("joao@email.com", Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA).toOccurrence();
            assertThat(o.isAnonymous()).isFalse();
        }
    }

    @Nested
    @DisplayName("Status e Prioridade inicial")
    class StatusAndPriority {

        @Test
        @DisplayName("status inicial é sempre PENDENTE")
        void statusIsPending() {
            Occurrence o = dto("joao@email.com", Occurrence.OccurrenceType.LIXO_ACUMULADO_OU_TERRENO_SUJO).toOccurrence();
            assertThat(o.getStatus()).isEqualTo(Occurrence.OccurrenceStatus.PENDENTE);
        }

        @Test
        @DisplayName("prioridade é calculada automaticamente pelo tipo (RN04)")
        void priorityIsAutoCalculated() {
            Occurrence alta  = dto(null, Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS).toOccurrence();
            Occurrence media = dto(null, Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA).toOccurrence();
            Occurrence baixa = dto(null, Occurrence.OccurrenceType.PROBLEMAS_EM_PRACAS_E_PARQUES).toOccurrence();

            assertThat(alta.getPriority()).isEqualTo(Occurrence.Priority.ALTA);
            assertThat(media.getPriority()).isEqualTo(Occurrence.Priority.MEDIA);
            assertThat(baixa.getPriority()).isEqualTo(Occurrence.Priority.BAIXA);
        }
    }

    @Nested
    @DisplayName("Mapeamento de campos")
    class FieldMapping {

        @Test
        @DisplayName("todos os campos simples são copiados corretamente")
        void mapsAllFields() {
            CreateOccurrenceDto d = new CreateOccurrenceDto();
            d.setDescription("Desc");
            d.setTitle("Título");
            d.setType(Occurrence.OccurrenceType.POSTE_COM_LUZ_QUEIMADA);
            d.setCity("Franca");
            d.setStreet("Rua A");
            d.setNumber("100");
            d.setNeighborhood("Centro");
            d.setLatitude(-20.538);
            d.setLongitude(-47.401);
            d.setEmail("joao@email.com");
            d.setCloudinaryPublicId("fc/img1");
            d.setUrlMedia("https://res.cloudinary.com/img1.jpg");

            Occurrence o = d.toOccurrence();

            assertThat(o.getDescription()).isEqualTo("Desc");
            assertThat(o.getTitle()).isEqualTo("Título");
            assertThat(o.getType()).isEqualTo(Occurrence.OccurrenceType.POSTE_COM_LUZ_QUEIMADA);
            assertThat(o.getCity()).isEqualTo("Franca");
            assertThat(o.getStreet()).isEqualTo("Rua A");
            assertThat(o.getNumber()).isEqualTo("100");
            assertThat(o.getNeighborhood()).isEqualTo("Centro");
            assertThat(o.getLatitude()).isEqualTo(-20.538);
            assertThat(o.getLongitude()).isEqualTo(-47.401);
            assertThat(o.getEmail()).isEqualTo("joao@email.com");
            assertThat(o.getCloudinaryPublicId()).isEqualTo("fc/img1");
            assertThat(o.getUrlMedia()).isEqualTo("https://res.cloudinary.com/img1.jpg");
        }
    }
}

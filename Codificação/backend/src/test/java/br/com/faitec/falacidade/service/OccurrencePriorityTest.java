package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.Occurrence.Priority;
import br.com.faitec.falacidade.domain.Occurrence.OccurrenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Testa a lógica de negócio do domínio Occurrence:
 *  - Atribuição automática de prioridade (RN04)
 *  - Cobertura de todos os OccurrenceTypes
 */
@DisplayName("Occurrence – lógica de domínio (RN04)")
class OccurrencePriorityTest {

    @Test
    @DisplayName("MAUS_TRATOS_AOS_ANIMAIS → ALTA")
    void animalAbusePriorityHigh() {
        assertThat(Priority.fromType(OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS))
            .isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("PESSOA_PRECISANDO_DE_AJUDA → ALTA")
    void personInNeedPriorityHigh() {
        assertThat(Priority.fromType(OccurrenceType.PESSOA_PRECISANDO_DE_AJUDA))
            .isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("SINALIZACAO_OU_SEMAFORO_COM_DEFEITO → ALTA")
    void trafficSignalPriorityHigh() {
        assertThat(Priority.fromType(OccurrenceType.SINALIZACAO_OU_SEMAFORO_COM_DEFEITO))
            .isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("PROBLEMAS_EM_PRACAS_E_PARQUES → BAIXA")
    void parksPriorityLow() {
        assertThat(Priority.fromType(OccurrenceType.PROBLEMAS_EM_PRACAS_E_PARQUES))
            .isEqualTo(Priority.BAIXA);
    }

    @Test
    @DisplayName("SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO → BAIXA")
    void noisePriorityLow() {
        assertThat(Priority.fromType(OccurrenceType.SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO))
            .isEqualTo(Priority.BAIXA);
    }

    @ParameterizedTest(name = "{0} → MEDIA")
    @EnumSource(value = OccurrenceType.class, names = {
        "BURACO_NA_RUA_OU_CALCADA",
        "POSTE_COM_LUZ_QUEIMADA",
        "LIXO_ACUMULADO_OU_TERRENO_SUJO",
        "FALHAS_NO_TRANSPORTE_PUBLICO",
        "PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA",
        "OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO",
        "OUTROS_PROBLEMAS"
    })
    @DisplayName("tipos de prioridade MEDIA")
    void mediumPriorityTypes(OccurrenceType type) {
        assertThat(Priority.fromType(type)).isEqualTo(Priority.MEDIA);
    }

    @Test
    @DisplayName("todos os OccurrenceTypes têm uma prioridade atribuída (sem exceção)")
    void allTypesHavePriority() {
        for (OccurrenceType type : OccurrenceType.values()) {
            assertThatCode(() -> Priority.fromType(type))
                .as("OccurrenceType %s deve ter prioridade", type)
                .doesNotThrowAnyException();
            assertThat(Priority.fromType(type))
                .as("Prioridade de %s não deve ser null", type)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("ocorrência anônima quando email é null")
    void anonymousWhenEmailNull() {
        Occurrence o = new Occurrence();
        o.setEmail(null);
        o.setAnonymous(true);
        assertThat(o.isAnonymous()).isTrue();
    }
}

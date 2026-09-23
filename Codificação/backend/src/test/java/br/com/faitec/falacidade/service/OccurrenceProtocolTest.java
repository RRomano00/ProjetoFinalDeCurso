package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.implementation.dao.postgres.OccurrencePostgresDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OccurrencePostgresDao — número de protocolo")
class OccurrenceProtocolTest {

    private static final String FORMATO = "FC-\\d{2}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}";

    @Mock Connection        connection;
    @Mock PreparedStatement conferencia;
    @Mock PreparedStatement insercao;
    @Mock ResultSet         jaExiste;
    @Mock ResultSet         chaveGerada;

    OccurrencePostgresDao sut;

    @BeforeEach
    void setUp() throws Exception {
        sut = new OccurrencePostgresDao(connection);
        when(connection.prepareStatement(anyString())).thenReturn(conferencia);
        when(conferencia.executeQuery()).thenReturn(jaExiste);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
            .thenReturn(insercao);
        when(insercao.getGeneratedKeys()).thenReturn(chaveGerada);
        when(chaveGerada.next()).thenReturn(true);
        when(chaveGerada.getInt(1)).thenReturn(7);
    }

    private Occurrence ocorrenciaAnonima() {
        Occurrence o = new Occurrence();
        o.setTitle("Buraco na pista");
        o.setDescription("Buraco fundo na altura do número 340");
        o.setCity("Santa Rita do Sapucaí");
        o.setType(Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA);
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        o.setAnonymous(true);
        return o;
    }

    @Test
    @DisplayName("gera no formato FC-AA-XXXXX, com onze caracteres")
    void geraNoFormatoCurto() throws Exception {
        when(jaExiste.next()).thenReturn(false);

        Occurrence o = ocorrenciaAnonima();
        sut.add(o);

        assertThat(o.getProtocolNumber()).matches(FORMATO).hasSize(11);
    }

    @Test
    @DisplayName("nunca usa O, 0, I ou 1 — os símbolos que se confundem à leitura")
    void evitaSimbolosAmbiguos() throws Exception {
        when(jaExiste.next()).thenReturn(false);

        for (int i = 0; i < 300; i++) {
            Occurrence o = ocorrenciaAnonima();
            sut.add(o);
            String sorteio = o.getProtocolNumber().substring(6);
            assertThat(sorteio).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}");
            assertThat(sorteio).doesNotContain("O").doesNotContain("0")
                               .doesNotContain("I").doesNotContain("1");
        }
    }

    @Test
    @DisplayName("sorteia de novo quando o protocolo já está em uso")
    void sorteiaDeNovoEmColisao() throws Exception {
        when(jaExiste.next()).thenReturn(true, false);

        Occurrence o = ocorrenciaAnonima();
        sut.add(o);

        assertThat(o.getProtocolNumber()).matches(FORMATO);
        verify(conferencia, times(2)).executeQuery();
    }
}

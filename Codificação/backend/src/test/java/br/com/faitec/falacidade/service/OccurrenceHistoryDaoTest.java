package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto;
import br.com.faitec.falacidade.implementation.dao.postgres.OccurrencePostgresDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // readHistory lê várias colunas; o teste fixa só as novas
@DisplayName("OccurrencePostgresDao – solicitação de conclusão no histórico")
class OccurrenceHistoryDaoTest {

    @Mock Connection connection;
    @Mock PreparedStatement ps;
    @Mock ResultSet rs;
    OccurrencePostgresDao sut;

    @BeforeEach void setUp() { sut = new OccurrencePostgresDao(connection); }

    @Test
    @DisplayName("grava a solicitação sem autor, com departamento, anexo e o status atual repetido")
    void insertsCompletionRequest() throws Exception {
        when(connection.prepareStatement(contains("INSERT INTO occurrence_history"))).thenReturn(ps);

        sut.insertCompletionRequest(10, 3, "Buraco tapado", "https://img/x.jpg");

        verify(connection).prepareStatement(contains("'COMPLETION_REQUEST'"));
        verify(ps).setString(1, "Buraco tapado");
        verify(ps).setInt(2, 3);
        verify(ps).setString(3, "https://img/x.jpg");
        verify(ps).setInt(4, 10);
        verify(ps).execute();
    }

    @Test
    @DisplayName("lê tipo, anexo e nome do departamento")
    void readsNewColumns() throws Exception {
        when(connection.prepareStatement(contains("FROM occurrence_history"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("new_status")).thenReturn("EM_ANDAMENTO");
        when(rs.getString("kind")).thenReturn("COMPLETION_REQUEST");
        when(rs.getString("attachment_url")).thenReturn("https://img/x.jpg");
        when(rs.getString("department_name")).thenReturn("Secretaria de Obras");
        when(rs.getInt("department_id")).thenReturn(3);

        OccurrenceHistoryDto h = sut.readHistory(10).get(0);

        assertThat(h.isCompletionRequest()).isTrue();
        assertThat(h.getAttachmentUrl()).isEqualTo("https://img/x.jpg");
        assertThat(h.getDepartmentName()).isEqualTo("Secretaria de Obras");
        assertThat(h.getDepartmentId()).isEqualTo(3);
    }
}

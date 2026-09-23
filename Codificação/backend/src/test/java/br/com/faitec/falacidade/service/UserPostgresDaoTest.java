package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.dao.postgres.UserPostgresDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPostgresDao")
class UserPostgresDaoTest {

    @Mock Connection        connection;
    @Mock PreparedStatement ps;
    @Mock ResultSet         rs;
    @Mock ResultSet         generatedKeys;

    UserPostgresDao sut;

    @BeforeEach
    void setUp() {
        sut = new UserPostgresDao(connection);
    }

    @Nested
    @DisplayName("add()")
    class Add {

        @Test
        @DisplayName("insere usuário e retorna id gerado")
        void returnsGeneratedId() throws Exception {
            when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
            when(ps.getGeneratedKeys()).thenReturn(generatedKeys);
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getInt(1)).thenReturn(99);

            UserModel user = new UserModel();
            user.setPassword("$2a$HASH");
            user.setFullname("Teste");
            user.setEmail("teste@email.com");
            user.setRole(UserModel.UserRole.CITIZEN);
            user.setState("sp");
            user.setAcceptsTerms(true);

            int id = sut.add(user);

            assertThat(id).isEqualTo(99);
            verify(ps).setString(1, "$2a$HASH");
            verify(ps).setString(2, "Teste");
            verify(ps).setString(3, "teste@email.com");
            verify(ps).setString(11, "SP");
            verify(ps).setString(12, "CITIZEN");
            verify(ps).setBoolean(14, true);
            verify(connection).commit();
        }

        @Test
        @DisplayName("faz rollback e lança RuntimeException quando SQLException ocorre")
        void rollsBackOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenThrow(new SQLException("constraint violation"));

            UserModel user = new UserModel();
            user.setPassword("$2a$HASH");
            user.setFullname("Teste");
            user.setEmail("dup@email.com");
            user.setRole(UserModel.UserRole.CITIZEN);

            assertThatThrownBy(() -> sut.add(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao inserir");

            verify(connection).rollback();
        }
    }

    @Nested
    @DisplayName("remove()")
    class Remove {

        @Test
        @DisplayName("executa DELETE com o id correto")
        void deletesById() throws Exception {
            when(connection.prepareStatement(contains("DELETE"))).thenReturn(ps);

            sut.remove(5);

            verify(ps).setInt(1, 5);
            verify(ps).execute();
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("erro"));

            assertThatThrownBy(() -> sut.remove(1))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("readById()")
    class ReadById {

        @Test
        @DisplayName("retorna UserModel mapeado quando encontrado")
        void returnsUser() throws Exception {
            when(connection.prepareStatement(contains("WHERE id"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);

            when(rs.getInt("id")).thenReturn(1);
            when(rs.getString("fullname")).thenReturn("João");
            when(rs.getString("email")).thenReturn("joao@email.com");
            when(rs.getString("password")).thenReturn("$2a$HASH");
            when(rs.getString("phone_number")).thenReturn("16999999999");
            when(rs.getString("street")).thenReturn("Rua A");
            when(rs.getString("neighborhood")).thenReturn("Centro");
            when(rs.getString("number")).thenReturn("100");
            when(rs.getString("cep")).thenReturn("14400-000");
            when(rs.getString("city")).thenReturn("Franca");
            when(rs.getString("state")).thenReturn("SP");
            when(rs.getBoolean("is_active")).thenReturn(true);
            when(rs.getBoolean("accepts_terms")).thenReturn(true);
            when(rs.getString("role")).thenReturn("CITIZEN");
            when(rs.getTimestamp("created_at")).thenReturn(null);
            when(rs.getTimestamp("updated_at")).thenReturn(null);

            UserModel user = sut.readById(1);

            assertThat(user).isNotNull();
            assertThat(user.getEmail()).isEqualTo("joao@email.com");
            assertThat(user.getRole()).isEqualTo(UserModel.UserRole.CITIZEN);
        }

        @Test
        @DisplayName("retorna null quando não encontrado")
        void returnsNullWhenNotFound() throws Exception {
            when(connection.prepareStatement(contains("WHERE id"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            assertThat(sut.readById(999)).isNull();
        }

        @Test
        @DisplayName("role 'USER' legado é convertido para CITIZEN")
        void convertsLegacyUserRoleToCitizen() throws Exception {
            when(connection.prepareStatement(contains("WHERE id"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getString("role")).thenReturn("USER");
            when(rs.getInt("id")).thenReturn(1);
            when(rs.getString("fullname")).thenReturn("X");
            when(rs.getString("email")).thenReturn("x@x.com");
            when(rs.getString("password")).thenReturn("h");
            when(rs.getString("phone_number")).thenReturn(null);
            when(rs.getString("street")).thenReturn(null);
            when(rs.getString("neighborhood")).thenReturn(null);
            when(rs.getString("number")).thenReturn(null);
            when(rs.getString("cep")).thenReturn(null);
            when(rs.getString("city")).thenReturn(null);
            when(rs.getString("state")).thenReturn(null);
            when(rs.getBoolean("is_active")).thenReturn(true);
            when(rs.getBoolean("accepts_terms")).thenReturn(false);
            when(rs.getTimestamp("created_at")).thenReturn(null);
            when(rs.getTimestamp("updated_at")).thenReturn(null);

            UserModel user = sut.readById(1);

            assertThat(user.getRole()).isEqualTo(UserModel.UserRole.CITIZEN);
        }
    }

    @Nested
    @DisplayName("readall()")
    class Readall {

        @Test
        @DisplayName("retorna lista vazia quando não há usuários ativos")
        void returnsEmptyList() throws Exception {
            when(connection.prepareStatement(contains("is_active"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            assertThat(sut.readall()).isEmpty();
        }
    }

    @Nested
    @DisplayName("readByEmail()")
    class ReadByEmail {

        @Test
        @DisplayName("retorna null quando e-mail não existe")
        void returnsNullWhenNotFound() throws Exception {
            when(connection.prepareStatement(contains("WHERE email"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            assertThat(sut.readByEmail("nao@existe.com")).isNull();
        }
    }

    @Nested
    @DisplayName("updatePassword()")
    class UpdatePassword {

        @Test
        @DisplayName("executa UPDATE com hash e id corretos e retorna true")
        void updatesPassword() throws Exception {
            when(connection.prepareStatement(contains("SET password"))).thenReturn(ps);
            when(ps.executeUpdate()).thenReturn(1);

            boolean result = sut.updatePassword(1, "$2a$NEWHASH");

            assertThat(result).isTrue();
            verify(ps).setString(1, "$2a$NEWHASH");
            verify(ps).setInt(2, 1);
            verify(ps).executeUpdate();
        }

        @Test
        @DisplayName("retorna false quando nenhuma linha foi alterada")
        void returnsFalseWhenNothingUpdated() throws Exception {
            when(connection.prepareStatement(contains("SET password"))).thenReturn(ps);
            when(ps.executeUpdate()).thenReturn(0);

            assertThat(sut.updatePassword(999, "$2a$NEWHASH")).isFalse();
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(contains("SET password")))
                .thenThrow(new SQLException("erro"));

            assertThatThrownBy(() -> sut.updatePassword(1, "$2a$HASH"))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("updateInformation()")
    class UpdateInformation {

        @Test
        @DisplayName("executa UPDATE com todos os campos do perfil")
        void updatesProfileFields() throws Exception {
            when(connection.prepareStatement(contains("SET fullname"))).thenReturn(ps);

            UserModel u = new UserModel();
            u.setFullname("Maria");
            u.setPhoneNumber("16999999999");
            u.setStreet("Rua B");
            u.setNeighborhood("Jardim X");
            u.setNumber("200");
            u.setCep("14400-001");
            u.setCity("Franca");
            u.setState("SP");

            sut.updateInformation(1, u);

            verify(ps).setString(1, "Maria");
            verify(ps).setString(7, "Franca");
            verify(ps).setString(8, "SP");
            verify(ps).setInt(9, 1);
            verify(ps).execute();
        }
    }

    @Nested
    @DisplayName("autocommit da conexão compartilhada")
    class AutoCommitState {

        private UserModel citizen() {
            UserModel user = new UserModel();
            user.setPassword("$2a$HASH");
            user.setFullname("Teste");
            user.setEmail("teste@email.com");
            user.setRole(UserModel.UserRole.CITIZEN);
            return user;
        }

        @Test
        @DisplayName("devolve a conexão ao autocommit depois de inserir")
        void restoresAutoCommitAfterInsert() throws Exception {
            when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);
            when(ps.getGeneratedKeys()).thenReturn(generatedKeys);
            when(generatedKeys.next()).thenReturn(true);

            sut.add(citizen());

            InOrder order = inOrder(connection);
            order.verify(connection).setAutoCommit(false);
            order.verify(connection).commit();
            order.verify(connection).setAutoCommit(true);
        }

        @Test
        @DisplayName("devolve a conexão ao autocommit mesmo quando o insert falha")
        void restoresAutoCommitOnFailure() throws Exception {
            when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenThrow(new SQLException("falha"));

            assertThatThrownBy(() -> sut.add(citizen())).isInstanceOf(RuntimeException.class);

            verify(connection).rollback();
            verify(connection).setAutoCommit(true);
        }
    }
}

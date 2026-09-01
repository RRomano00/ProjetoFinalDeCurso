package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.user.UserServiceImpl;
import br.com.faitec.falacidade.port.dao.user.UserDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock UserDao         userDao;
    @Mock PasswordEncoder passwordEncoder;

    // Mockito vai injetar via construtor (userDao + passwordEncoder)
    @InjectMocks UserServiceImpl sut;

    // Encoder real usado apenas para verificar o hash nos testes de encoding
    final BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();

    private UserModel citizenWith(String email, String password) {
        UserModel u = new UserModel();
        u.setFullname("João Silva");
        u.setEmail(email);
        u.setPassword(password);
        u.setRole(UserModel.UserRole.CITIZEN);
        u.setAcceptsTerms(true);
        u.setActive(true);
        return u;
    }

    private UserModel citizenInDb(int id, String encodedPassword) {
        UserModel u = citizenWith("joao@email.com", encodedPassword);
        u.setId(id);
        return u;
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("cria usuário e retorna id gerado")
        void success() {
            when(passwordEncoder.encode(any())).thenReturn("$2a$HASH");
            when(userDao.add(any())).thenReturn(42);
            assertThat(sut.create(citizenWith("joao@email.com", "Senha@123"))).isEqualTo(42);
        }

        @Test @DisplayName("delega encoding ao PasswordEncoder injetado")
        void usesInjectedEncoder() {
            when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$ENCODED");
            when(userDao.add(any())).thenReturn(1);
            sut.create(citizenWith("a@b.com", "Senha@123"));
            verify(passwordEncoder).encode("Senha@123");
        }

        @Test @DisplayName("entity null → -1")
        void nullEntity() {
            assertThat(sut.create(null)).isEqualTo(-1);
            verifyNoInteractions(userDao);
        }

        @Test @DisplayName("nome em branco → -1")
        void blankName() {
            UserModel u = citizenWith("a@b.com", "Senha@123");
            u.setFullname("  ");
            assertThat(sut.create(u)).isEqualTo(-1);
        }

        @Test @DisplayName("CITIZEN sem aceitar termos → -1")
        void citizenWithoutTerms() {
            UserModel u = citizenWith("a@b.com", "Senha@123");
            u.setAcceptsTerms(false);
            assertThat(sut.create(u)).isEqualTo(-1);
        }

        @Test @DisplayName("EMPLOYEE não precisa aceitar termos")
        void employeeNoTermsNeeded() {
            when(passwordEncoder.encode(any())).thenReturn("$2a$HASH");
            when(userDao.add(any())).thenReturn(5);
            UserModel u = citizenWith("f@b.com", "Senha@123");
            u.setRole(UserModel.UserRole.EMPLOYEE);
            u.setAcceptsTerms(false);
            assertThat(sut.create(u)).isEqualTo(5);
        }

        @Test @DisplayName("senha < 8 chars → -1")
        void shortPassword() {
            assertThat(sut.create(citizenWith("a@b.com", "Ab@1"))).isEqualTo(-1);
        }

        @Test @DisplayName("senha sem número → -1")
        void noDigit() {
            assertThat(sut.create(citizenWith("a@b.com", "Senha@@@"))).isEqualTo(-1);
        }

        @Test @DisplayName("senha sem especial → -1")
        void noSpecial() {
            assertThat(sut.create(citizenWith("a@b.com", "Senha1234"))).isEqualTo(-1);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {
        @Test @DisplayName("delega ao DAO")
        void delegates() { sut.delete(1); verify(userDao).remove(1); }

        @Test @DisplayName("id negativo ignorado")
        void negative() { sut.delete(-1); verifyNoInteractions(userDao); }
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test @DisplayName("retorna usuário encontrado")
        void found() {
            when(userDao.readById(1)).thenReturn(citizenInDb(1, "h"));
            assertThat(sut.findById(1)).isNotNull();
        }

        @Test @DisplayName("id negativo → null sem chamar DAO")
        void negative() {
            assertThat(sut.findById(-1)).isNull();
            verifyNoInteractions(userDao);
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {
        @Test @DisplayName("retorna lista do DAO")
        void returnsList() {
            when(userDao.readall()).thenReturn(List.of(citizenInDb(1,"h")));
            assertThat(sut.findAll()).hasSize(1);
        }
    }

    @Nested @DisplayName("findByEmail()")
    class FindByEmail {
        @Test @DisplayName("retorna usuário por e-mail")
        void found() {
            when(userDao.readByEmail("a@b.com")).thenReturn(citizenInDb(1,"h"));
            assertThat(sut.findByEmail("a@b.com")).isNotNull();
        }

        @Test @DisplayName("e-mail null → null sem DAO")
        void nullEmail() {
            assertThat(sut.findByEmail(null)).isNull();
            verifyNoInteractions(userDao);
        }
    }

    @Nested @DisplayName("updatePassword()")
    class UpdatePassword {
        @Test @DisplayName("troca senha quando antiga está correta")
        void success() {
            when(userDao.readById(1)).thenReturn(citizenInDb(1, "$2a$HASH"));
            when(passwordEncoder.matches("Antiga@1", "$2a$HASH")).thenReturn(true);
            when(passwordEncoder.encode("Nova@Senha9")).thenReturn("$2a$NEW");
            when(userDao.updatePassword(1, "$2a$NEW")).thenReturn(true);
            assertThat(sut.updatePassword(1, "Antiga@1", "Nova@Senha9")).isTrue();
        }

        @Test @DisplayName("senha antiga errada → false")
        void wrongOld() {
            when(userDao.readById(1)).thenReturn(citizenInDb(1, "$2a$HASH"));
            when(passwordEncoder.matches("Errada@1", "$2a$HASH")).thenReturn(false);
            assertThat(sut.updatePassword(1, "Errada@1", "Nova@Senha9")).isFalse();
            verify(userDao, never()).updatePassword(anyInt(), anyString());
        }
    }

    @Nested @DisplayName("updatePasswordEncoded()")
    class UpdatePasswordEncoded {
        @Test @DisplayName("persiste hash direto")
        void success() {
            when(userDao.updatePassword(1, "$2a$H")).thenReturn(true);
            assertThat(sut.updatePasswordEncoded(1, "$2a$H")).isTrue();
        }

        @Test @DisplayName("id negativo → false")
        void negative() {
            assertThat(sut.updatePasswordEncoded(-1, "$2a$H")).isFalse();
            verifyNoInteractions(userDao);
        }
    }
}

package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.implementation.service.department.DepartmentServiceImpl;
import br.com.faitec.falacidade.port.dao.department.DepartmentDao;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock DepartmentDao departmentDao;
    DepartmentServiceImpl sut;

    @BeforeEach
    void setUp() { sut = new DepartmentServiceImpl(departmentDao); }

    private Department novo(String name, String email) {
        return novo(name, email, "Santa Rita do Sapucaí", "MG");
    }

    private Department novo(String name, String email, String city, String state) {
        Department d = new Department();
        d.setName(name);
        d.setEmail(email);
        d.setCity(city);
        d.setState(state);
        return d;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("cadastra e devolve o id gerado")
        void createsDepartment() {
            when(departmentDao.add(any())).thenReturn(7);

            int id = sut.create(novo("Secretaria de Obras", "obras@prefeitura.exemplo.br"));

            assertThat(id).isEqualTo(7);
        }

        @Test
        @DisplayName("apara os espaços e grava o e-mail em minúsculas")
        void normalizesInput() {
            when(departmentDao.add(any())).thenReturn(1);

            sut.create(novo("  Secretaria de Meio Ambiente  ", "  MeioAmbiente@Prefeitura.Exemplo.BR ",
                            "  Santa Rita do Sapucaí ", "mg"));

            ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
            verify(departmentDao).add(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Secretaria de Meio Ambiente");
            assertThat(captor.getValue().getEmail()).isEqualTo("meioambiente@prefeitura.exemplo.br");
            assertThat(captor.getValue().getCity()).isEqualTo("Santa Rita do Sapucaí");
            assertThat(captor.getValue().getState()).isEqualTo("MG");
        }

        @Test
        @DisplayName("recusa nome já cadastrado no mesmo município")
        void rejectsDuplicateNameInSameCity() {
            when(departmentDao.existsByNameInCity("Secretaria de Obras", "Santa Rita do Sapucaí", "MG"))
                .thenReturn(true);

            assertThatThrownBy(() -> sut.create(novo("Secretaria de Obras", "novo@prefeitura.exemplo.br")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neste município");

            verify(departmentDao, never()).add(any());
        }

        @Test
        @DisplayName("aceita o mesmo nome em outro município")
        void acceptsSameNameInAnotherCity() {
            when(departmentDao.existsByNameInCity("Secretaria de Obras", "Itajubá", "MG")).thenReturn(false);
            when(departmentDao.add(any())).thenReturn(12);

            int id = sut.create(novo("Secretaria de Obras", "obras@itajuba.exemplo.br", "Itajubá", "MG"));

            assertThat(id).isEqualTo(12);
        }

        @Test
        @DisplayName("recusa e-mail já cadastrado, ainda que em outro município")
        void rejectsDuplicateEmail() {
            when(departmentDao.existsByEmail("obras@prefeitura.exemplo.br")).thenReturn(true);

            assertThatThrownBy(() -> sut.create(novo("Outro Setor", "obras@prefeitura.exemplo.br", "Itajubá", "MG")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("e-mail");

            verify(departmentDao, never()).add(any());
        }

        @Test
        @DisplayName("a restrição do banco ainda barra a duplicata em requisições simultâneas")
        void rejectsWhenDatabaseConstraintFires() {
            when(departmentDao.add(any())).thenReturn(-1);

            assertThatThrownBy(() -> sut.create(novo("Setor X", "x@prefeitura.exemplo.br")))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("recusa campos obrigatórios vazios e e-mail inválido")
        void rejectsInvalidInput() {
            assertThatThrownBy(() -> sut.create(novo("   ", "ok@exemplo.br")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.create(novo("Setor", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.create(novo("Setor", "sem-arroba")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.create(novo("Setor", "ok@exemplo.br", "  ", "MG")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.create(novo("Setor", "ok@exemplo.br", "Itajubá", "")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.create(null))
                .isInstanceOf(IllegalArgumentException.class);

            verify(departmentDao, never()).add(any());
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("grava a edição já normalizada")
        void updatesNormalized() {
            when(departmentDao.update(any())).thenReturn(true);

            sut.update(4, novo("  Secretaria de Obras  ", " Obras@Prefeitura.BR ", " Itajubá ", "mg"));

            ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
            verify(departmentDao).update(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(4);
            assertThat(captor.getValue().getName()).isEqualTo("Secretaria de Obras");
            assertThat(captor.getValue().getEmail()).isEqualTo("obras@prefeitura.br");
            assertThat(captor.getValue().getState()).isEqualTo("MG");
        }

        @Test
        @DisplayName("nome ou e-mail de outro setor barra a edição")
        void rejectsDuplicate() {
            when(departmentDao.update(any())).thenReturn(false);

            assertThatThrownBy(() -> sut.update(4, novo("Secretaria de Obras", "obras@prefeitura.br")))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("id inválido ou dado obrigatório ausente não chega ao banco")
        void guardsInvalidInput() {
            assertThatThrownBy(() -> sut.update(0, novo("Setor", "ok@exemplo.br")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.update(4, novo("  ", "ok@exemplo.br")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.update(4, novo("Setor", "sem-arroba")))
                .isInstanceOf(IllegalArgumentException.class);

            verify(departmentDao, never()).update(any());
        }
    }

    @Test
    @DisplayName("findAllByCity() não consulta o banco sem município ou UF")
    void findAllByCityGuardsEmptyLocality() {
        assertThat(sut.findAllByCity("", "MG")).isEmpty();
        assertThat(sut.findAllByCity("Itajubá", null)).isEmpty();
        verify(departmentDao, never()).readAllByCity(anyString(), anyString());
    }

    @Test
    @DisplayName("findById() não consulta o banco com id inválido")
    void findByIdGuardsInvalidId() {
        assertThat(sut.findById(0)).isNull();
        assertThat(sut.findById(-3)).isNull();
        verify(departmentDao, never()).readById(anyInt());
    }
}

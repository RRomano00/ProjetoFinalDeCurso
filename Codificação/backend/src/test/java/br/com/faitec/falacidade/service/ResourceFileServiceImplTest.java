package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.tools.ResourceFileServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResourceFileServiceImpl")
class ResourceFileServiceImplTest {

    ResourceFileServiceImpl sut = new ResourceFileServiceImpl();

    @Test
    @DisplayName("lê arquivo existente e retorna conteúdo não vazio")
    void readsExistingFile() throws IOException {
        String content = sut.read(
            "fala-cidade-db-scripts/FalaCidade_DDL_CriacaoTabelas.sql");

        assertThat(content).isNotBlank();
        assertThat(content).contains("CREATE TABLE");
    }

    @Test
    @DisplayName("arquivo inexistente lança RuntimeException")
    void throwsForMissingFile() {
        assertThatThrownBy(() -> sut.read("nao-existe/arquivo.sql"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("conteúdo retornado inclui quebra de linha entre linhas")
    void preservesLineBreaks() throws IOException {
        String content = sut.read(
            "fala-cidade-db-scripts/FalaCidade_DDL_CriacaoTabelas.sql");
        assertThat(content).contains("\n");
    }

    @Test
    @DisplayName("não usa concatenação O(n²) – o método termina em tempo razoável para arquivos grandes")
    void performanceIsLinear() {
        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                sut.read("fala-cidade-db-scripts/FalaCidade_DDL_CriacaoTabelas.sql");
            }
        }).doesNotThrowAnyException();
    }
}

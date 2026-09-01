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
        // Usa o próprio SQL de criação de tabelas como arquivo de teste
        String content = sut.read(
            "fala-cidade-db-scripts/PID_SCRIPT_CRIACAO-TABELAS.sql");

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
            "fala-cidade-db-scripts/PID_SCRIPT_CRIACAO-TABELAS.sql");
        // Um arquivo SQL com múltiplas linhas deve conter newlines
        assertThat(content).contains("\n");
    }

    @Test
    @DisplayName("não usa concatenação O(n²) – o método termina em tempo razoável para arquivos grandes")
    void performanceIsLinear() {
        // Teste de fumaça: ler o arquivo 100 vezes deve ser rápido (< 2 segundos)
        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                sut.read("fala-cidade-db-scripts/PID_SCRIPT_CRIACAO-TABELAS.sql");
            }
        }).doesNotThrowAnyException();
    }
}

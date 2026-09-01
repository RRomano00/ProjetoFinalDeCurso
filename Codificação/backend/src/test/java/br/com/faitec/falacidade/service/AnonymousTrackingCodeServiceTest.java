package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.tracking.AnonymousTrackingCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AnonymousTrackingCodeService")
class AnonymousTrackingCodeServiceTest {

    AnonymousTrackingCodeService sut = new AnonymousTrackingCodeService();

    // ================================================================
    // generateCode()
    // ================================================================

    @Nested
    @DisplayName("generateCode()")
    class GenerateCode {

        @Test
        @DisplayName("gera código com exatamente 8 caracteres")
        void has8Chars() {
            assertThat(sut.generateCode()).hasSize(8);
        }

        @Test
        @DisplayName("código contém apenas caracteres do alfabeto seguro (sem O, 0, I, 1)")
        void usesOnlySafeAlphabet() {
            // Gera 500 códigos e verifica que nenhum contém caracteres ambíguos
            for (int i = 0; i < 500; i++) {
                String code = sut.generateCode();
                assertThat(code)
                    .doesNotContain("O", "0", "I", "1")
                    .matches("[A-Z2-9]{8}");
            }
        }

        @Test
        @DisplayName("código é maiúsculo")
        void isUpperCase() {
            String code = sut.generateCode();
            assertThat(code).isEqualTo(code.toUpperCase());
        }

        @RepeatedTest(10)
        @DisplayName("códigos gerados repetidamente são únicos (aleatoriedade)")
        void codesAreUnique() {
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                codes.add(sut.generateCode());
            }
            // Com 1000 gerações, esperamos quase nenhuma colisão
            assertThat(codes.size()).isGreaterThan(995);
        }
    }

    // ================================================================
    // hash()
    // ================================================================

    @Nested
    @DisplayName("hash()")
    class Hash {

        @Test
        @DisplayName("retorna string de 64 caracteres hexadecimais (SHA-256)")
        void returns64HexChars() {
            String hash = sut.hash("A3KP7NB2");
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("mesmo código sempre gera mesmo hash (determinístico)")
        void isDeterministic() {
            String h1 = sut.hash("A3KP7NB2");
            String h2 = sut.hash("A3KP7NB2");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("códigos diferentes geram hashes diferentes")
        void differentCodesHaveDifferentHashes() {
            assertThat(sut.hash("A3KP7NB2")).isNotEqualTo(sut.hash("B4LQ8MC3"));
        }

        @Test
        @DisplayName("hash não contém o código original (não é reversível)")
        void hashDoesNotContainOriginalCode() {
            String code = "A3KP7NB2";
            assertThat(sut.hash(code)).doesNotContain(code);
        }

        @Test
        @DisplayName("não lança exceção para strings de qualquer comprimento")
        void doesNotThrowForAnyInput() {
            assertThatCode(() -> sut.hash("")).doesNotThrowAnyException();
            assertThatCode(() -> sut.hash("A")).doesNotThrowAnyException();
            assertThatCode(() -> sut.hash("A".repeat(1000))).doesNotThrowAnyException();
        }
    }

    // ================================================================
    // matches()
    // ================================================================

    @Nested
    @DisplayName("matches()")
    class Matches {

        @Test
        @DisplayName("código correto bate com o hash armazenado")
        void matchesCorrectCode() {
            String code = "A3KP7NB2";
            String hash = sut.hash(code);
            assertThat(sut.matches(code, hash)).isTrue();
        }

        @Test
        @DisplayName("código errado não bate com o hash")
        void doesNotMatchWrongCode() {
            String hash = sut.hash("A3KP7NB2");
            assertThat(sut.matches("XXXXXXXX", hash)).isFalse();
        }

        @Test
        @DisplayName("código null retorna false sem exceção")
        void nullCodeReturnsFalse() {
            String hash = sut.hash("A3KP7NB2");
            assertThat(sut.matches(null, hash)).isFalse();
        }

        @Test
        @DisplayName("hash null retorna false sem exceção")
        void nullHashReturnsFalse() {
            assertThat(sut.matches("A3KP7NB2", null)).isFalse();
        }

        @Test
        @DisplayName("ambos null retornam false")
        void bothNullReturnsFalse() {
            assertThat(sut.matches(null, null)).isFalse();
        }

        @Test
        @DisplayName("código minúsculo bate com hash gerado do maiúsculo (normalização)")
        void lowercaseCodeMatchesUppercaseHash() {
            String hash = sut.hash("A3KP7NB2");
            // O cidadão pode digitar em minúsculo — matches() deve normalizar
            assertThat(sut.matches("a3kp7nb2", hash)).isTrue();
        }

        @Test
        @DisplayName("código com espaços extras bate após trim")
        void codeWithSpacesMatchesAfterTrim() {
            String hash = sut.hash("A3KP7NB2");
            assertThat(sut.matches("  A3KP7NB2  ", hash)).isTrue();
        }
    }
}

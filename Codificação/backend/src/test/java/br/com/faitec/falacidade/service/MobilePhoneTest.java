package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.MobilePhone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MobilePhone – celular brasileiro para o SMS")
class MobilePhoneTest {

    @Test
    @DisplayName("aceita o celular com máscara, com DDI, com + e só com dígitos")
    void normalizaFormatosComuns() {
        assertThat(MobilePhone.toE164("(35) 99876-1234")).isEqualTo("+5535998761234");
        assertThat(MobilePhone.toE164("+55 35 99876-1234")).isEqualTo("+5535998761234");
        assertThat(MobilePhone.toE164("35998761234")).isEqualTo("+5535998761234");
        assertThat(MobilePhone.toE164("5535998761234")).isEqualTo("+5535998761234");
    }

    @Test
    @DisplayName("DDD 55 (RS) não é confundido com o DDI")
    void ddd55() {
        assertThat(MobilePhone.toE164("(55) 99988-7766")).isEqualTo("+5555999887766");
        assertThat(MobilePhone.toE164("5555999887766")).isEqualTo("+5555999887766");
    }

    @Test
    @DisplayName("recusa fixo, vazio, curto, zero de operadora e DDD inexistente")
    void recusaInvalidos() {
        assertThat(MobilePhone.toE164(null)).isNull();
        assertThat(MobilePhone.toE164("")).isNull();
        assertThat(MobilePhone.toE164("1234")).isNull();
        assertThat(MobilePhone.toE164("(35) 3471-1234")).isNull();
        assertThat(MobilePhone.toE164("(35) 89876-1234")).isNull();
        assertThat(MobilePhone.toE164("035998761234")).isNull();
        assertThat(MobilePhone.toE164("(30) 99876-1234")).isNull();
        assertThat(MobilePhone.toE164("(05) 99876-1234")).isNull();
    }

    @Test
    @DisplayName("mascara o número mostrando só o DDD e os quatro finais")
    void mascara() {
        assertThat(MobilePhone.mask("+5535998761234")).isEqualTo("(35) 9****-1234");
        assertThat(MobilePhone.mask(null)).isNull();
    }
}

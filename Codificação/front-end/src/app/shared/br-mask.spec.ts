import { FormControl } from '@angular/forms';
import { maskCep, maskPhone, maskUf, cepValidator, mobileValidator, personNameValidator } from './br-mask';

describe('máscaras brasileiras', () => {
  it('CEP: só dígitos, no máximo 8, hífen automático', () => {
    expect(maskCep('')).toBe('');
    expect(maskCep('375')).toBe('375');
    expect(maskCep('37540')).toBe('37540');
    expect(maskCep('375400')).toBe('37540-0');
    expect(maskCep('37540000')).toBe('37540-000');
    expect(maskCep('37540-000123')).toBe('37540-000');
    expect(maskCep('ab37.540 00x0')).toBe('37540-000');
  });

  it('telefone: (DD) 9XXXX-XXXX, até 11 dígitos', () => {
    expect(maskPhone('3')).toBe('(3');
    expect(maskPhone('35')).toBe('(35');
    expect(maskPhone('359')).toBe('(35) 9');
    expect(maskPhone('3599999')).toBe('(35) 99999');
    expect(maskPhone('35999998888')).toBe('(35) 99999-8888');
    expect(maskPhone('+55 (35) 99999-8888 9')).toBe('(35) 99999-8888');
    expect(maskPhone('3533334444')).toBe('(35) 3333-4444');
  });

  it('UF: só letras, maiúsculas, 2', () => {
    expect(maskUf('mg')).toBe('MG');
    expect(maskUf('m1g2s')).toBe('MG');
  });

  it('validadores: vazio passa (o required cuida); incompleto não', () => {
    expect(cepValidator(new FormControl(''))).toBeNull();
    expect(cepValidator(new FormControl('37540-000'))).toBeNull();
    expect(cepValidator(new FormControl('37540-00'))).toEqual({ cep: true });

    expect(mobileValidator(new FormControl(''))).toBeNull();
    expect(mobileValidator(new FormControl('(35) 99999-8888'))).toBeNull();
    expect(mobileValidator(new FormControl('(35) 3333-4444'))).toEqual({ mobile: true });
    expect(mobileValidator(new FormControl('(05) 99999-8888'))).toEqual({ mobile: true });
    expect(mobileValidator(new FormControl('(35) 9999'))).toEqual({ mobile: true });
  });

  it('nome: letras (com acento), espaço, apóstrofo, hífen e ponto; sem números', () => {
    expect(personNameValidator(new FormControl(''))).toBeNull();
    expect(personNameValidator(new FormControl("Maria D'Ávila Souza-Lima Jr."))).toBeNull();
    expect(personNameValidator(new FormControl('João 2'))).toEqual({ personName: true });
    expect(personNameValidator(new FormControl('@@'))).toEqual({ personName: true });
  });
});

import { FormControl } from '@angular/forms';
import { brBirthDateValidator, brDateToIso, maskBrDate } from './br-date';

describe('br-date', () => {
  it('põe as barras enquanto a pessoa digita e ignora o que não é dígito', () => {
    expect(maskBrDate('3')).toBe('3');
    expect(maskBrDate('3112')).toBe('31/12');
    expect(maskBrDate('31121990')).toBe('31/12/1990');
    expect(maskBrDate('31/12/19905')).toBe('31/12/1990');
    expect(maskBrDate('ab31c12')).toBe('31/12');
  });

  it('converte dd/mm/aaaa para o formato do backend', () => {
    expect(brDateToIso('31/12/1990')).toBe('1990-12-31');
    expect(brDateToIso('29/02/2024')).toBe('2024-02-29');
  });

  it('recusa datas que não existem no calendário ou incompletas', () => {
    for (const v of ['29/02/2023', '31/04/2000', '00/01/2000', '01/13/2000', '1/1/2000', '']) {
      expect(brDateToIso(v)).withContext(v).toBeNull();
    }
  });

  it('valida a data de nascimento: existente, não futura e depois de 1900', () => {
    const erros = (v: string) => new FormControl(v, brBirthDateValidator).errors;
    expect(erros('31/12/1990')).toBeNull();
    expect(erros('')).toBeNull();
    expect(erros('01/01/2999')).toEqual({ futureDate: true });
    expect(erros('31/02/2000')).toEqual({ brDate: true });
    expect(erros('31/12/19')).toEqual({ brDate: true });
    expect(erros('01/01/1800')).toEqual({ brDate: true });
  });
});

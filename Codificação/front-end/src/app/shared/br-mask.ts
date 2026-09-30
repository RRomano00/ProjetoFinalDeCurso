import { AbstractControl, ValidationErrors } from '@angular/forms';

const digits = (raw: string, max: number) => (raw || '').replace(/\D/g, '').slice(0, max);

/** CEP enquanto a pessoa digita: só dígitos, no máximo 8, com o hífen no lugar. */
export function maskCep(raw: string): string {
  const d = digits(raw, 8);
  return d.length <= 5 ? d : `${d.slice(0, 5)}-${d.slice(5)}`;
}

/** Telefone: (DD) 9XXXX-XXXX; com 10 dígitos, (DD) XXXX-XXXX. Um +55 colado é ignorado. */
export function maskPhone(raw: string): string {
  let d = (raw || '').replace(/\D/g, '');
  if (d.length > 11 && d.startsWith('55')) d = d.slice(2);
  d = d.slice(0, 11);
  if (!d) return '';
  if (d.length <= 2) return `(${d}`;
  const local = d.slice(2);
  // Celular (começa com 9) quebra depois do 5º dígito; fixo, depois do 4º — já durante a digitação.
  const split = local.startsWith('9') ? 5 : 4;
  return local.length <= split
    ? `(${d.slice(0, 2)}) ${local}`
    : `(${d.slice(0, 2)}) ${local.slice(0, split)}-${local.slice(split)}`;
}

/** UF: duas letras maiúsculas. */
export function maskUf(raw: string): string {
  return (raw || '').replace(/[^A-Za-z]/g, '').slice(0, 2).toUpperCase();
}

// Vazio passa: quando o campo é obrigatório, quem avisa é o Validators.required.
export function cepValidator(c: AbstractControl): ValidationErrors | null {
  return !c.value || /^\d{5}-\d{3}$/.test(c.value) ? null : { cep: true };
}

/** Celular: é o que recebe SMS (o backend recusa fixo e DDD com zero). */
export function mobileValidator(c: AbstractControl): ValidationErrors | null {
  return !c.value || /^\([1-9][1-9]\) 9\d{4}-\d{4}$/.test(c.value) ? null : { mobile: true };
}

export function personNameValidator(c: AbstractControl): ValidationErrors | null {
  const v = (c.value || '').trim();
  return !v || /^\p{L}[\p{L}\p{M} '’.-]*$/u.test(v) ? null : { personName: true };
}

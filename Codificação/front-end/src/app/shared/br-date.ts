import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Máscara dd/mm/aaaa enquanto a pessoa digita: só dígitos, com as barras no lugar. */
export function maskBrDate(raw: string): string {
  const d = (raw || '').replace(/\D/g, '').slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/** "31/12/1990" → "1990-12-31", o formato que o backend recebe; null se a data não existir. */
export function brDateToIso(value: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value || '');
  if (!m) return null;
  const [, dd, mm, yyyy] = m;
  // O Date "corrige" 31/02 para 03/03; se os campos voltam diferentes, a data não existe.
  const date = new Date(Date.UTC(+yyyy, +mm - 1, +dd));
  if (date.getUTCFullYear() !== +yyyy || date.getUTCMonth() !== +mm - 1 || date.getUTCDate() !== +dd) return null;
  return `${yyyy}-${mm}-${dd}`;
}

/** Vazio fica com o Validators.required; aqui só a data completa, existente e não futura. */
export function brBirthDateValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  const iso = brDateToIso(control.value);
  if (!iso || iso < '1900-01-01') return { brDate: true };
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  return iso > today ? { futureDate: true } : null;
}

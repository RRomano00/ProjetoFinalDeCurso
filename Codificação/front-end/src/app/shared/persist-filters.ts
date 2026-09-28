/**
 * Troca os campos de filtro da tela por propriedades que se gravam no
 * localStorage a cada alteração e voltam na próxima visita. O valor inicial do
 * campo vale como padrão enquanto nada foi salvo. A chave leva o id da conta,
 * para quem divide o aparelho não herdar os filtros de outra pessoa, e o
 * prefixo "filters." sobrevive ao logout (AuthenticationService.endSession).
 */
export function persistFilters<T extends object>(target: T, screen: string, fields: (keyof T & string)[]) {
  const key = `filters.${localStorage.getItem('id') || 'visitante'}.${screen}`;
  let saved: Record<string, unknown> = {};
  try { saved = JSON.parse(localStorage.getItem(key) || '{}') || {}; } catch { }

  const values: Record<string, unknown> = {};
  for (const field of fields) {
    values[field] = field in saved ? saved[field] : (target as any)[field];
    Object.defineProperty(target, field, {
      get: () => values[field],
      set: value => {
        values[field] = value;
        try { localStorage.setItem(key, JSON.stringify(values)); } catch { }
      },
      enumerable: true,
      configurable: true,
    });
  }
}

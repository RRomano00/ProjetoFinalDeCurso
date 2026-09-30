import { OccurrenceHistory } from './model/occurrence';

/**
 * A solicitação de conclusão que ainda vale (histórico do mais recente para o mais antigo):
 * a mais recente, se depois dela não houve troca real de status nem reencaminhamento ao mesmo
 * departamento. Mesma regra do backend (DepartmentAccessRestController.pendingRequestAt).
 */
export function pendingCompletionRequest(history: OccurrenceHistory[]): OccurrenceHistory | null {
  const later: OccurrenceHistory[] = [];
  for (const h of history) {
    if (h.kind === 'COMPLETION_REQUEST') {
      const stale = later.some(s => (s.oldStatus && s.oldStatus !== s.newStatus)
                                 || (s.departmentId != null && s.departmentId === h.departmentId));
      return stale ? null : h;
    }
    later.push(h);
  }
  return null;
}

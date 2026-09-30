import { OccurrenceHistory } from './model/occurrence';
import { pendingCompletionRequest } from './completion-request';

// Histórico vem do mais recente para o mais antigo.
const req = (dep: number): OccurrenceHistory => ({ kind: 'COMPLETION_REQUEST', newStatus: 'EM_ANDAMENTO', oldStatus: 'EM_ANDAMENTO', departmentId: dep });
const st = (oldStatus: string, newStatus: string, departmentId?: number): OccurrenceHistory =>
  ({ kind: 'STATUS', oldStatus, newStatus, departmentId });

describe('pendingCompletionRequest', () => {
  it('a solicitação mais recente vale se nada mudou depois', () => {
    const pedido = req(3);
    expect(pendingCompletionRequest([pedido, st('PENDENTE', 'EM_ANDAMENTO', 3)])).toBe(pedido);
  });

  it('reaberta ou concluída depois: a solicitação antiga não vale mais', () => {
    expect(pendingCompletionRequest([st('CONCLUIDA', 'EM_ANDAMENTO'), st('EM_ANDAMENTO', 'CONCLUIDA'), req(3)])).toBeNull();
  });

  it('reencaminhada ao mesmo departamento depois: não vale mais', () => {
    expect(pendingCompletionRequest([st('EM_ANDAMENTO', 'EM_ANDAMENTO', 3), req(3)])).toBeNull();
  });

  it('só a mais recente vale; resposta da equipe sem troca de status não invalida', () => {
    const nova = req(3);
    expect(pendingCompletionRequest([st('EM_ANDAMENTO', 'EM_ANDAMENTO'), nova, req(3)])).toBe(nova);
  });

  it('sem solicitação: null', () => {
    expect(pendingCompletionRequest([st('PENDENTE', 'EM_ANDAMENTO')])).toBeNull();
  });
});

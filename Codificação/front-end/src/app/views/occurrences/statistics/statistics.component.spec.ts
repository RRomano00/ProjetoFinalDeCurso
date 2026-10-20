import { StatisticsComponent } from './statistics.component';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { Occurrence } from '../../../domain/model/occurrence';

const occ = (createdAt: string): Occurrence => ({ description: 'x', city: 'X', status: 'PENDENTE', createdAt });

describe('StatisticsComponent — filtro de período', () => {
  let tela: StatisticsComponent;

  beforeEach(() => {
    localStorage.clear();
    tela = new StatisticsComponent({} as OccurrenceReadService);
    tela.occurrences = [occ('2026-09-01T10:00:00'), occ('2026-09-15T10:00:00'), occ('2026-10-01T10:00:00')];
  });
  afterEach(() => localStorage.clear());

  it('filtra pelas datas digitadas em dd/mm/aaaa, com os extremos inclusos', () => {
    tela.filterDateFrom = '01/09/2026';
    tela.filterDateTo = '15/09/2026';
    tela.recompute();
    expect(tela.total).toBe(2);
  });

  it('data incompleta ou inexistente não filtra; só a completa inválida vira erro', () => {
    tela.filterDateFrom = '01/09';
    tela.filterDateTo = '31/02/2026';
    tela.recompute();
    expect(tela.total).toBe(3);
    expect(tela.invalidDate('01/09')).toBeFalse();
    expect(tela.invalidDate('31/02/2026')).toBeTrue();
  });

  it('converte o filtro salvo no formato antigo (aaaa-mm-dd)', () => {
    localStorage.setItem('filters.visitante.statistics', JSON.stringify({ filterDateFrom: '2026-09-15' }));
    expect(new StatisticsComponent({} as OccurrenceReadService).filterDateFrom).toBe('15/09/2026');
  });
});

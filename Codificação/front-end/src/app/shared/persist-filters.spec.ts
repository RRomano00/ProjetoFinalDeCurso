import { persistFilters } from './persist-filters';

class Tela {
  search  = '';
  groupBy = 'neighborhood';
  constructor() { persistFilters(this, 'teste', ['search', 'groupBy']); }
}

describe('persistFilters', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('id', '7');
  });
  afterEach(() => localStorage.clear());

  it('usa o valor inicial como padrão quando nada foi salvo', () => {
    expect(new Tela().groupBy).toBe('neighborhood');
  });

  it('restaura na próxima visita o que foi alterado', () => {
    const primeira = new Tela();
    primeira.search = 'buraco';
    primeira.groupBy = '';

    const segunda = new Tela();
    expect(segunda.search).toBe('buraco');
    expect(segunda.groupBy).toBe('');
  });

  it('não mistura os filtros de contas diferentes', () => {
    new Tela().search = 'buraco';
    localStorage.setItem('id', '8');
    expect(new Tela().search).toBe('');
  });
});

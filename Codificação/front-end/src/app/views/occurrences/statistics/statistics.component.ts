import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { typeLabel, typeColor } from '../../../domain/occurrence-labels';

export interface ChartBar          { label: string; value: number; color: string; pct: number; }
export interface NeighborhoodStat  { neighborhood: string; total: number; pct: number; }
export interface StatusSlice       { label: string; value: number; pct: number; token: string; }
export interface AgeBucket         { label: string; value: number; pct: number; token: string; }
export interface SupportedItem     { id?: number; protocol: string; title: string; supports: number; }

const DIA = 86_400_000;

@Component({
  selector: 'app-statistics',
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './statistics.component.html',
  styleUrl: './statistics.component.css'
})
export class StatisticsComponent implements OnInit {
  occurrences: Occurrence[] = [];
  loading = true;

  filterDateFrom     = '';
  filterDateTo       = '';
  filterNeighborhood = '';
  filterType         = '';

  total = 0;
  pending = 0; inProgress = 0; resolved = 0; rejected = 0;
  open = 0;
  highPriorityOpen = 0;
  resolutionRate = 0;
  avgResolutionDays = '—';
  statusSlices:   StatusSlice[]      = [];
  ageBuckets:     AgeBucket[]        = [];
  oldestOpen:     Occurrence | null  = null;
  oldestOpenDays  = 0;
  barChartData:     ChartBar[]          = [];
  topNeighborhoods: NeighborhoodStat[]  = [];
  mostSupported:    SupportedItem[]     = [];

  constructor(private occurrenceReadService: OccurrenceReadService) {}

  async ngOnInit() {
    try {
      this.occurrences = await this.occurrenceReadService.findAll() || [];
    } catch {
      this.occurrences = [];
    }
    this.loading = false;
    this.recompute();
  }

  get hasActiveFilters(): boolean {
    return !!(this.filterDateFrom || this.filterDateTo || this.filterNeighborhood || this.filterType);
  }

  clearFilters() {
    this.filterDateFrom = this.filterDateTo = this.filterNeighborhood = this.filterType = '';
    this.recompute();
  }

  get neighborhoodOptions(): string[] {
    const set = new Set<string>();
    this.occurrences.forEach(o => set.add(o.neighborhood?.trim() || 'Não informado'));
    return Array.from(set).sort();
  }

  get typeOptions(): { value: string; label: string }[] {
    const set = new Set<string>();
    this.occurrences.forEach(o => { if (o.type) set.add(o.type); });
    return Array.from(set).sort().map(t => ({ value: t, label: typeLabel(t) }));
  }

  private filtrar(): Occurrence[] {
    return this.occurrences.filter(o => {
      const criada = o.createdAt ? o.createdAt.substring(0, 10) : '';
      return (!this.filterDateFrom || (criada && criada >= this.filterDateFrom))
          && (!this.filterDateTo   || (criada && criada <= this.filterDateTo))
          && (!this.filterNeighborhood ||
              (o.neighborhood?.trim() || 'Não informado') === this.filterNeighborhood)
          && (!this.filterType || o.type === this.filterType);
    });
  }

  recompute() {
    const f = this.filtrar();
    const agora = Date.now();

    this.total      = f.length;
    this.pending    = f.filter(o => o.status === 'PENDENTE').length;
    this.inProgress = f.filter(o => o.status === 'EM_ANDAMENTO').length;
    this.resolved   = f.filter(o => o.status === 'CONCLUIDA').length;
    this.rejected   = f.filter(o => o.status === 'INDEFERIDA').length;

    const emAberto = f.filter(o => o.status === 'PENDENTE' || o.status === 'EM_ANDAMENTO');
    this.open             = emAberto.length;
    this.highPriorityOpen = emAberto.filter(o => o.priority === 'ALTA').length;
    this.resolutionRate   = this.total ? Math.round((this.resolved / this.total) * 100) : 0;

    this.statusSlices = [
      { label: 'Pendentes',    value: this.pending,    token: 'st-pending'  },
      { label: 'Em andamento', value: this.inProgress, token: 'st-progress' },
      { label: 'Concluídas',   value: this.resolved,   token: 'st-done'     },
      { label: 'Indeferidas',  value: this.rejected,   token: 'st-refused'  },
    ].filter(s => s.value > 0)
     .map(s => ({ ...s, pct: this.total ? (s.value / this.total) * 100 : 0 }));

    this.calcularTempoMedio(f);
    this.calcularFila(emAberto, agora);

    const porTipo: Record<string, number> = {};
    f.forEach(o => { const k = o.type ?? 'OUTROS_PROBLEMAS'; porTipo[k] = (porTipo[k] || 0) + 1; });
    const maiorTipo = Math.max(...Object.values(porTipo), 1);
    this.barChartData = Object.entries(porTipo).sort((a, b) => b[1] - a[1]).map(([t, q]) => ({
      label: typeLabel(t), value: q, color: typeColor(t), pct: (q / maiorTipo) * 100,
    }));

    const porBairro: Record<string, number> = {};
    f.forEach(o => { const b = o.neighborhood?.trim() || 'Não informado'; porBairro[b] = (porBairro[b] || 0) + 1; });
    const maiorBairro = Math.max(...Object.values(porBairro), 1);
    this.topNeighborhoods = Object.entries(porBairro).sort((a, b) => b[1] - a[1]).slice(0, 8)
      .map(([neighborhood, total]) => ({ neighborhood, total, pct: (total / maiorBairro) * 100 }));

    this.mostSupported = emAberto
      .filter(o => (o.supportCount ?? 0) > 0)
      .sort((a, b) => (b.supportCount ?? 0) - (a.supportCount ?? 0))
      .slice(0, 5)
      .map(o => ({
        id: o.id,
        protocol: o.protocolNumber ?? '—',
        title: o.title?.trim() || typeLabel(o.type ?? 'OUTROS_PROBLEMAS'),
        supports: o.supportCount ?? 0,
      }));
  }

  private calcularTempoMedio(f: Occurrence[]) {
    const concluidas = f.filter(o => o.status === 'CONCLUIDA' && o.createdAt && o.updatedAt);
    if (!concluidas.length) { this.avgResolutionDays = '—'; return; }
    const soma = concluidas.reduce((s, o) =>
      s + (new Date(o.updatedAt!).getTime() - new Date(o.createdAt!).getTime()), 0);
    const dias = soma / concluidas.length / DIA;
    if (dias < 1) { this.avgResolutionDays = `${Math.max(1, Math.round(dias * 24))} h`; return; }
    const inteiro = Math.round(dias);
    this.avgResolutionDays = `${inteiro} ${inteiro === 1 ? 'dia' : 'dias'}`;
  }

  private calcularFila(emAberto: Occurrence[], agora: number) {
    const faixas = [
      { label: 'até 3 dias',    token: 'age-fresh', min: 0,  max: 3   },
      { label: '4 a 7 dias',    token: 'age-warm',  min: 4,  max: 7   },
      { label: '8 a 15 dias',   token: 'age-hot',   min: 8,  max: 15  },
      { label: 'mais de 15',    token: 'age-late',  min: 16, max: Infinity },
    ];
    const contagem = faixas.map(() => 0);
    let maisAntiga: Occurrence | null = null;
    let maiorEspera = -1;

    for (const o of emAberto) {
      if (!o.createdAt) continue;
      const dias = Math.floor((agora - new Date(o.createdAt).getTime()) / DIA);
      const i = faixas.findIndex(fx => dias >= fx.min && dias <= fx.max);
      if (i >= 0) contagem[i]++;
      if (dias > maiorEspera) { maiorEspera = dias; maisAntiga = o; }
    }

    const maior = Math.max(...contagem, 1);
    this.ageBuckets = faixas.map((fx, i) => ({
      label: fx.label, value: contagem[i], token: fx.token, pct: (contagem[i] / maior) * 100,
    }));
    this.oldestOpen     = maisAntiga;
    this.oldestOpenDays = Math.max(maiorEspera, 0);
  }
}

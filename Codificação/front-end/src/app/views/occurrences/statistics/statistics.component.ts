import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { typeLabel, typeColor } from '../../../domain/occurrence-labels';

export interface ChartBar   { label: string; value: number; color: string; pct: number; }
export interface DonutSlice { label: string; value: number; color: string; pct: number; offset: number; dash: number; }
export interface NeighborhoodStat { neighborhood: string; total: number; pct: number; }

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

  constructor(private occurrenceReadService: OccurrenceReadService) {}

  async ngOnInit() {
    try {
      this.occurrences = await this.occurrenceReadService.findAll() || [];
    } catch {
      this.occurrences = [];
    }
    this.loading = false;
  }

  get filtered(): Occurrence[] {
    return this.occurrences.filter(o => {
      const created = o.createdAt ? o.createdAt.substring(0, 10) : '';
      const matchFrom  = !this.filterDateFrom || (created && created >= this.filterDateFrom);
      const matchTo    = !this.filterDateTo   || (created && created <= this.filterDateTo);
      const matchHood  = !this.filterNeighborhood ||
        (o.neighborhood?.trim() || 'Não informado') === this.filterNeighborhood;
      const matchType  = !this.filterType || o.type === this.filterType;
      return matchFrom && matchTo && matchHood && matchType;
    });
  }

  get hasActiveFilters(): boolean {
    return !!(this.filterDateFrom || this.filterDateTo || this.filterNeighborhood || this.filterType);
  }

  clearFilters() {
    this.filterDateFrom = '';
    this.filterDateTo = '';
    this.filterNeighborhood = '';
    this.filterType = '';
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

  get total()       { return this.filtered.length; }
  get pending()     { return this.filtered.filter(o => o.status === 'PENDENTE').length; }
  get inProgress()  { return this.filtered.filter(o => o.status === 'EM_ANDAMENTO').length; }
  get resolved()    { return this.filtered.filter(o => o.status === 'ATENDIDA').length; }
  get rejected()    { return this.filtered.filter(o => o.status === 'INDEFERIDA').length; }
  get resolutionRate() {
    return this.total > 0 ? Math.round((this.resolved / this.total) * 100) : 0;
  }

  get avgResolutionDays(): string {
    const done = this.filtered.filter(o =>
      o.status === 'ATENDIDA' && o.createdAt && o.updatedAt);
    if (done.length === 0) return '—';
    const totalMs = done.reduce((sum, o) =>
      sum + (new Date(o.updatedAt!).getTime() - new Date(o.createdAt!).getTime()), 0);
    const days = totalMs / done.length / 86_400_000;
    if (days < 1) return `${Math.max(1, Math.round(days * 24))} h`;
    // Dia é unidade inteira: "26,0 dias" sugere uma precisão que a medida não tem.
    const inteiro = Math.round(days);
    return `${inteiro} ${inteiro === 1 ? 'dia' : 'dias'}`;
  }

  get barChartData(): ChartBar[] {
    const map: Record<string, number> = {};
    this.filtered.forEach(o => { const k = o.type ?? 'OUTROS_PROBLEMAS'; map[k] = (map[k] || 0) + 1; });
    const max = Math.max(...Object.values(map), 1);
    return Object.entries(map).sort((a, b) => b[1] - a[1]).map(([type, qty]) => ({
      label: typeLabel(type),
      value: qty,
      color: typeColor(type),
      pct:   Math.round((qty / max) * 100),
    }));
  }

  get donutSlices(): DonutSlice[] {
    const CIRC = 2 * Math.PI * 54;
    const data = [
      { label: 'Pendente',     value: this.pending,    color: '#8a5800' },
      { label: 'Em Andamento', value: this.inProgress, color: '#14487e' },
      { label: 'Atendida',     value: this.resolved,   color: '#176242' },
      { label: 'Indeferida',   value: this.rejected,   color: '#8b3a2d' },
    ].filter(d => d.value > 0);
    let accumulated = 0;
    return data.map(d => {
      const pct  = this.total > 0 ? d.value / this.total : 0;
      const dash = pct * CIRC;
      const slice: DonutSlice = { ...d, pct: Math.round(pct * 100), dash, offset: CIRC - accumulated };
      accumulated += dash;
      return slice;
    });
  }

  get donutCirc() { return 2 * Math.PI * 54; }

  get topNeighborhoods(): NeighborhoodStat[] {
    const map: Record<string, number> = {};
    this.filtered.forEach(o => { const b = o.neighborhood?.trim() || 'Não informado'; map[b] = (map[b] || 0) + 1; });
    const max = Math.max(...Object.values(map), 1);
    return Object.entries(map).sort((a, b) => b[1] - a[1]).slice(0, 8)
      .map(([neighborhood, total]) => ({ neighborhood, total, pct: Math.round((total / max) * 100) }));
  }
}

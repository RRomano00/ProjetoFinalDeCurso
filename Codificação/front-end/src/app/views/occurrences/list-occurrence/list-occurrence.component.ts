import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { typeLabel, typeColor, statusLabel, statusClass, priorityClass } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-list-occurrence',
  imports: [RouterModule, CommonModule, FormsModule],
  templateUrl: './list-occurrence.component.html',
  styleUrl: './list-occurrence.component.css'
})
export class ListOccurrenceComponent implements OnInit {
  occurrences: Occurrence[] = [];
  filtered:    Occurrence[] = [];
  loading = true;

  searchProtocol = '';
  filterStatus   = '';
  filterType     = '';

  /** RF12: agrupamento de ocorrências semelhantes ('' = sem agrupamento). */
  groupBy: '' | 'type' | 'neighborhood' | 'status' = '';

  statusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'ATENDIDA', 'INDEFERIDA'];

  // Acompanhamento de ocorrência ANÔNIMA pelo código gerado (ex: UXNLTPSU)
  showProtocolModal = false;
  trackingCodeInput = '';
  searchingProtocol = false;

  constructor(
    private occurrenceReadService: OccurrenceReadService,
    private router: Router,
    private toastr: ToastrService
  ) {}

  async ngOnInit() {
    try {
      this.occurrences = await this.occurrenceReadService.findAll();
      this.filtered    = [...this.occurrences];
    } catch {
      this.toastr.error('Erro ao carregar ocorrências.');
    } finally {
      this.loading = false;
    }
  }

  applyFilters() {
    this.filtered = this.occurrences.filter(o => {
      const matchProtocol = !this.searchProtocol ||
        o.protocolNumber?.toLowerCase().includes(this.searchProtocol.toLowerCase());
      const matchStatus = !this.filterStatus || o.status === this.filterStatus;
      const matchType   = !this.filterType   || o.type   === this.filterType;
      return matchProtocol && matchStatus && matchType;
    });
  }

  clearFilters() {
    this.searchProtocol = '';
    this.filterStatus   = '';
    this.filterType     = '';
    this.groupBy        = '';
    this.filtered = [...this.occurrences];
  }

  /** RF12: agrupa as ocorrências filtradas pelo critério escolhido. */
  get groups(): { label: string; color: string | null; items: Occurrence[] }[] {
    if (!this.groupBy) return [];
    const map = new Map<string, Occurrence[]>();
    for (const o of this.filtered) {
      const key =
        this.groupBy === 'type'         ? (o.type || 'OUTROS_PROBLEMAS') :
        this.groupBy === 'neighborhood' ? (o.neighborhood?.trim() || 'Sem bairro informado') :
                                          (o.status || '');
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(o);
    }
    return [...map.entries()]
      .map(([key, items]) => ({
        label: this.groupBy === 'type'   ? this.typeLabel(key)
             : this.groupBy === 'status' ? this.statusLabel(key)
             : key,
        color: this.groupBy === 'type' ? this.typeColor(key) : null,
        items
      }))
      .sort((a, b) => b.items.length - a.items.length);   // grupos maiores primeiro
  }

  // ── Acompanhar ocorrência anônima pelo código gerado (popup) ──
  openProtocolModal() { this.showProtocolModal = true; this.trackingCodeInput = ''; }
  closeProtocolModal() { this.showProtocolModal = false; }

  async searchByCode() {
    const code = this.trackingCodeInput.trim().toUpperCase();
    if (!code) { this.toastr.warning('Digite o código de acompanhamento.'); return; }
    this.searchingProtocol = true;
    try {
      const occ = await this.occurrenceReadService.findAnonymous(code);
      if (occ && occ.id) {
        this.showProtocolModal = false;
        this.router.navigate(['/occurrence/detail', occ.id]);
      } else {
        this.toastr.error('Nenhuma ocorrência encontrada com esse código.');
      }
    } catch {
      this.toastr.error('Nenhuma ocorrência encontrada com esse código.');
    } finally {
      this.searchingProtocol = false;
    }
  }

  // Labels e cores compartilhados (domain/occurrence-labels)
  statusLabel   = statusLabel;
  statusClass   = statusClass;
  priorityClass = priorityClass;
  typeLabel     = typeLabel;
  typeColor     = typeColor;
}

import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
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
  filterMine     = false;

  supportedIds = new Set<number>();
  supportingId: number | null = null;

  private readonly userRole = localStorage.getItem('role') || '';
  private readonly myEmail  = localStorage.getItem('email') || '';

  get isVisitor(): boolean { return !localStorage.getItem('token'); }
  get canSupport(): boolean { return this.userRole === 'CITIZEN' || this.isVisitor; }
  get canFilterMine(): boolean { return this.userRole === 'CITIZEN'; }

  groupBy: '' | 'type' | 'neighborhood' | 'status' = '';

  statusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'ATENDIDA', 'INDEFERIDA'];

  showProtocolModal = false;
  trackingCodeInput = '';
  searchingProtocol = false;

  constructor(
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceSupportService: OccurrenceSupportService,
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
    await this.loadMySupports();
  }

  private async loadMySupports() {
    if (this.isVisitor) return;
    try {
      this.supportedIds = new Set(await this.occurrenceSupportService.mySupports());
    } catch {
      this.supportedIds = new Set();
    }
  }

  isSupported(id?: number): boolean {
    return id != null && this.supportedIds.has(id);
  }

  async support(o: Occurrence) {
    if (this.isVisitor) {
      this.toastr.info('Entre na sua conta para apoiar uma ocorrência.');
      this.router.navigate(['/account/sign-in']);
      return;
    }
    if (o.id == null || this.isSupported(o.id) || this.supportingId != null) return;
    this.supportingId = o.id;
    try {
      const info = await this.occurrenceSupportService.support(o.id);
      o.supportCount = info.count;
      this.supportedIds.add(o.id);
      this.toastr.success('Apoio registrado. Obrigado!');
    } catch {
      this.toastr.error('Não foi possível registrar o apoio.');
    } finally {
      this.supportingId = null;
    }
  }

  /** Desfaz o apoio. O botão só existe nos cartões que o usuário já apoia. */
  async unsupport(o: Occurrence) {
    if (this.isVisitor) return;
    if (o.id == null || !this.isSupported(o.id) || this.supportingId != null) return;
    this.supportingId = o.id;
    try {
      const info = await this.occurrenceSupportService.unsupport(o.id);
      o.supportCount = info.count;
      this.supportedIds.delete(o.id);
      this.toastr.info('Apoio removido.');
    } catch {
      this.toastr.error('Não foi possível remover o apoio.');
    } finally {
      this.supportingId = null;
    }
  }

  applyFilters() {
    this.filtered = this.occurrences.filter(o => {
      const matchProtocol = !this.searchProtocol ||
        o.protocolNumber?.toLowerCase().includes(this.searchProtocol.toLowerCase());
      const matchStatus = !this.filterStatus || o.status === this.filterStatus;
      const matchType   = !this.filterType   || o.type   === this.filterType;
      const matchMine   = !this.filterMine   || (!!o.email && o.email === this.myEmail);
      return matchProtocol && matchStatus && matchType && matchMine;
    });
  }

  clearFilters() {
    this.searchProtocol = '';
    this.filterStatus   = '';
    this.filterType     = '';
    this.filterMine     = false;
    this.groupBy        = '';
    this.filtered = [...this.occurrences];
  }

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

  statusLabel   = statusLabel;
  statusClass   = statusClass;
  priorityClass = priorityClass;
  typeLabel     = typeLabel;
  typeColor     = typeColor;
}

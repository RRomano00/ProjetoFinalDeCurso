import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { typeLabel, typeColor, statusLabel, statusClass, priorityLabel } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';
import { AuthenticationService } from '../../../services/security/authentication.service';

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

  search = '';
  filterStatus   = '';
  filterType     = '';
  filterMine     = false;

  supportedIds = new Set<number>();
  supportingId: number | null = null;

  private readonly myEmail = localStorage.getItem('email') || '';

  groupBy: '' | 'type' | 'neighborhood' | 'status' = '';

  statusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'ATENDIDA', 'INDEFERIDA'];

  /** RF08/RF11: visitante tentou apoiar → pede login (mesmo convite do detalhe). */
  showLoginPrompt = false;

  showProtocolModal = false;
  trackingCodeInput = '';
  searchingProtocol = false;

  constructor(
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceSupportService: OccurrenceSupportService,
    private router: Router,
    public  auth: AuthenticationService,
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
    if (this.auth.isVisitor()) return;
    try {
      this.supportedIds = new Set(await this.occurrenceSupportService.mySupports());
    } catch {
      this.supportedIds = new Set();
    }
  }

  isSupported(id?: number): boolean {
    return id != null && this.supportedIds.has(id);
  }

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  /**
   * Apoia ou desfaz o apoio no mesmo botão. Quem manda no estado é a resposta
   * do servidor (`supportedByMe`) — antes a tela dava `add()` no conjunto local
   * e passava a exibir "Você apoia" mesmo quando o back-end não gravou nada.
   */
  async toggleSupport(o: Occurrence) {
    if (this.auth.isVisitor()) { this.showLoginPrompt = true; return; }
    if (o.id == null || this.supportingId != null) return;
    const apoiando = this.isSupported(o.id);
    this.supportingId = o.id;
    try {
      const info = await this.occurrenceSupportService.toggle(o.id, apoiando);
      o.supportCount = info.count;
      if (info.supportedByMe) this.supportedIds.add(o.id);
      else                    this.supportedIds.delete(o.id);
      this.toastr[apoiando ? 'info' : 'success'](
        apoiando ? 'Apoio removido.' : 'Apoio registrado. Obrigado!');
    } catch {
      this.toastr.error(apoiando
        ? 'Não foi possível remover o apoio.'
        : 'Não foi possível registrar o apoio.');
    } finally {
      this.supportingId = null;
    }
  }

  /** Minúsculas e sem acento: quem digita "onibus" tem que achar "ônibus". */
  private fold(text?: string): string {
    return (text || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
  }

  applyFilters() {
    const term = this.fold(this.search.trim());
    this.filtered = this.occurrences.filter(o => {
      const matchSearch = !term
        || this.fold(o.protocolNumber).includes(term)
        || this.fold(o.title).includes(term);
      const matchStatus = !this.filterStatus || o.status === this.filterStatus;
      const matchType   = !this.filterType   || o.type   === this.filterType;
      const matchMine   = !this.filterMine   || (!!o.email && o.email === this.myEmail);
      return matchSearch && matchStatus && matchType && matchMine;
    });
  }

  clearFilters() {
    this.search         = '';
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
  priorityLabel = priorityLabel;
  typeLabel     = typeLabel;
  typeColor     = typeColor;
}

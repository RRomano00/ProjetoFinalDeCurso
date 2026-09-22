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
import { LocalityPreferenceService, Municipality } from '../../../services/local/locality-preference.service';

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
  /** Município em exibição (vazio = todos) — a mesma escolha do mapa. */
  filterCity     = '';
  municipalityOptions: Municipality[] = [];
  municipalityLabel = LocalityPreferenceService.label;
  cityKey           = LocalityPreferenceService.fold;
  filterStatus   = '';
  filterType     = '';
  filterMine     = false;

  supportedIds = new Set<number>();
  supportingId: number | null = null;

  private readonly myEmail = localStorage.getItem('email') || '';

  groupBy: '' | 'type' | 'neighborhood' | 'status' = '';

  statusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'INDEFERIDA'];

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
    private locality: LocalityPreferenceService,
    private toastr: ToastrService
  ) {}

  /**
   * "Minhas" vem do servidor, por autoria, e não de um recorte da listagem
   * pública: a ocorrência que a pessoa registrou em outro município precisa
   * aparecer aqui mesmo quando não estiver na lista geral.
   */
  private mine: Occurrence[] | null = null;
  loadingMine = false;

  async ngOnInit() {
    try {
      this.occurrences = await this.occurrenceReadService.findAll();
      await this.loadMunicipalities();
      this.applyFilters();
    } catch {
      this.toastr.error('Erro ao carregar ocorrências.');
    } finally {
      this.loading = false;
    }
    await this.loadMySupports();
  }

  /**
   * Opções do filtro: os municípios da lista, mais o escolhido e o do cadastro
   * — estes podem ainda não ter nenhuma ocorrência registrada.
   */
  private async loadMunicipalities() {
    const chosen = this.locality.choice;
    this.municipalityOptions = LocalityPreferenceService.options(
      this.occurrences, [chosen, await this.locality.ofCurrentUser()]);
    if (chosen) this.filterCity = LocalityPreferenceService.fold(chosen.city);
  }

  /** Município escolhido, ou null quando a listagem está global. */
  get selectedMunicipality(): Municipality | null {
    return this.municipalityOptions.find(
      m => LocalityPreferenceService.fold(m.city) === this.filterCity) || null;
  }

  /** A escolha vale para o mapa também: é a mesma preferência. */
  onCityChange() {
    this.locality.choice = this.selectedMunicipality;
    this.applyFilters();
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

  /** Liga/desliga "somente as minhas": busca a lista por autoria na primeira vez. */
  async toggleMine() {
    if (this.filterMine && this.mine === null && !this.auth.isVisitor()) {
      this.loadingMine = true;
      try {
        this.mine = await this.occurrenceReadService.findMine();
      } catch {
        this.mine = null;   // sem a lista do servidor, recai no recorte local
      } finally {
        this.loadingMine = false;
      }
    }
    this.applyFilters();
  }

  applyFilters() {
    const term = this.fold(this.search.trim());
    // Com "minhas" ligado, a fonte é a lista por autoria; sem ele, a pública.
    const source = this.filterMine && this.mine !== null ? this.mine : this.occurrences;
    const chosen = this.selectedMunicipality;
    this.filtered = source.filter(o => {
      const matchSearch = !term
        || this.fold(o.protocolNumber).includes(term)
        || this.fold(o.title).includes(term);
      const matchStatus = !this.filterStatus || o.status === this.filterStatus;
      const matchType   = !this.filterType   || o.type   === this.filterType;
      const matchCity   = LocalityPreferenceService.matches(chosen, o.city, o.state);
      // Com a lista por autoria, tudo que veio já é da pessoa; o recorte local
      // continua valendo como reserva quando o servidor não respondeu.
      const matchMine   = !this.filterMine || this.mine !== null
                       || (!!o.email && o.email === this.myEmail);
      return matchSearch && matchStatus && matchType && matchMine && matchCity;
    });
  }

  clearFilters() {
    this.search         = '';
    this.filterCity     = '';
    this.filterStatus   = '';
    this.filterType     = '';
    this.filterMine     = false;
    this.groupBy        = '';
    this.locality.choice = null;
    this.applyFilters();
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

import { Component, OnInit, OnDestroy, NgZone } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { OccurrenceEditService } from '../../../services/occurrence-edit.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
import { GeocodingService } from '../../../services/local/geocoding.service';
import { Occurrence, OccurrenceHistory } from '../../../domain/model/occurrence';
import { typeLabel, typeColor, statusLabel, statusColor } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

@Component({
  selector: 'app-detail-occurrence',
  imports: [RouterModule, CommonModule, FormsModule],
  templateUrl: './detail-occurrence.component.html',
  styleUrl: './detail-occurrence.component.css'
})
export class DetailOccurrenceComponent implements OnInit, OnDestroy {
  occurrence?: Occurrence;
  loading = true;
  updating = false;
  showRejectForm = false;
  rejectObservation = '';

  private map?: L.Map;

  userRole = localStorage.getItem('role') || '';

  // RF16: apoios
  supportCount = 0;
  supportedByMe = false;
  supporting = false;
  /** RF08/RF11: visitante tentou apoiar → pede login. */
  showLoginPrompt = false;

  // RN03/RF11: histórico de mudanças de status
  history: OccurrenceHistory[] = [];

  // Mensagem opcional do funcionário ao mudar o status (vai no e-mail e no histórico)
  staffMessage = '';
  readonly presetMessages = [
    'Encaminhada para o setor responsável.',
    'Equipe designada para avaliação no local.',
    'Serviço agendado.',
    'Serviço executado e finalizado.'
  ];

  // RF12: grupo de duplicatas + atualização coletiva
  group: Occurrence[] = [];
  applyToGroup = false;
  get groupSize(): number { return this.group.length; }

  get canUpdateStatus(): boolean {
    return this.userRole === 'EMPLOYEE' || this.userRole === 'ADMINISTRATOR';
  }

  /** Visitante sem conta (RF08/RF11). */
  get isVisitor(): boolean {
    return !localStorage.getItem('token');
  }

  /** RF16: cidadão apoia; visitante vê o botão mas é convidado a entrar. */
  get canSupport(): boolean {
    return this.userRole === 'CITIZEN' || this.isVisitor;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceEditService: OccurrenceEditService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    private toastr: ToastrService,
    private ngZone: NgZone
  ) {}

  async ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.router.navigate(['/occurrence/list']); return; }
    try {
      this.occurrence = await this.occurrenceReadService.findById(id);
      setTimeout(() => this.renderMap(), 0);
      this.loadSupportInfo(id);
      this.loadHistory(id);
      if (this.canUpdateStatus) this.loadGroup(id);
    } catch {
      this.toastr.error('Ocorrência não encontrada.');
      this.router.navigate(['/occurrence/list']);
    } finally { this.loading = false; }
  }

  // ── RF16: apoios ──
  private async loadSupportInfo(id: string) {
    try {
      const info = await this.occurrenceSupportService.getSupportInfo(id);
      this.supportCount  = info.count;
      this.supportedByMe = info.supportedByMe;
    } catch { /* silencioso: apenas não mostra o contador */ }
  }

  /** RN03/RF11: carrega a linha do tempo de status (inclui justificativa do indeferimento). */
  private async loadHistory(id: string) {
    try { this.history = await this.occurrenceReadService.getHistory(id); }
    catch { this.history = []; }
  }

  /** RF12: carrega as ocorrências encadeadas (só exibido para a equipe). */
  private async loadGroup(id: string) {
    try { this.group = await this.occurrenceReadService.getGroup(id); }
    catch { this.group = []; }
  }

  /** Preenche a caixa de mensagem com um texto pré-definido. */
  applyPreset(text: string) { this.staffMessage = text; }

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  async support() {
    // Visitante: não pode apoiar — convida a entrar (RF11)
    if (this.isVisitor) { this.showLoginPrompt = true; return; }
    if (!this.occurrence?.id || this.supportedByMe) return;
    this.supporting = true;
    try {
      const info = await this.occurrenceSupportService.support(this.occurrence.id);
      this.supportCount  = info.count;
      this.supportedByMe = true;
      this.toastr.success('Apoio registrado. Obrigado!');
    } catch {
      this.toastr.error('Não foi possível registrar o apoio.');
    } finally {
      this.supporting = false;
    }
  }

  ngOnDestroy() { if (this.map) this.map.remove(); }

  private async renderMap() {
    const o = this.occurrence;
    if (!o) return;

    let lat = o.latitude ?? null;
    let lng = o.longitude ?? null;

    // Sem coordenadas salvas: tenta geocodificar o endereço; senão, centro de Santa Rita
    if (lat == null || lng == null) {
      const coords = await this.geocodingService.geocode(o.street || '', o.neighborhood || '', o.city || '');
      if (coords) { lat = coords.lat; lng = coords.lng; }
    }

    const hasPoint = lat != null && lng != null;
    const center: [number, number] = hasPoint
      ? [lat as number, lng as number]
      : [SANTA_RITA_DO_SAPUCAI.lat, SANTA_RITA_DO_SAPUCAI.lng];

    this.ngZone.runOutsideAngular(() => {
      this.map = L.map('detail-map', { zoomControl: true })
        .setView(center, hasPoint ? 17 : DEFAULT_MAP_ZOOM);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors', maxZoom: 19
      }).addTo(this.map);
      setTimeout(() => this.map!.invalidateSize(), 100);

      if (hasPoint) {
        const color = statusColor(o.status);
        const icon = L.divIcon({
          className: '',
          html: `<div style="width:16px;height:16px;background:${color};border:2px solid #fff;
                 border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.4)"></div>`,
          iconSize: [16, 16], iconAnchor: [8, 8]
        });
        L.marker(center, { icon }).addTo(this.map!)
          .bindPopup(`<b>${o.protocolNumber || ''}</b><br>${this.statusLabel(o.status)}`);
      }
    });
  }

  async updateToInProgress() {
    if (!this.occurrence?.id) return;
    this.updating = true;
    try {
      await this.occurrenceEditService.updateToInProgress(
        String(this.occurrence.id), this.staffMessage.trim() || undefined, this.applyToGroup);
      this.occurrence!.status = 'EM_ANDAMENTO';
      this.toastr.success(this.applyToGroup
        ? `Grupo de ${this.groupSize} ocorrências atualizado para Em Andamento.`
        : 'Status atualizado para Em Andamento.');
      this.afterStatusChange();
    } catch { this.toastr.error('Erro ao atualizar status.'); }
    finally { this.updating = false; }
  }

  async updateToConclude() {
    if (!this.occurrence?.id) return;
    this.updating = true;
    try {
      await this.occurrenceEditService.updateToConclude(
        String(this.occurrence.id), this.staffMessage.trim() || undefined, this.applyToGroup);
      this.occurrence!.status = 'ATENDIDA';
      this.toastr.success(this.applyToGroup
        ? `Grupo de ${this.groupSize} ocorrências marcado como Atendida.`
        : 'Ocorrência marcada como Atendida.');
      this.afterStatusChange();
    } catch { this.toastr.error('Erro ao atualizar status.'); }
    finally { this.updating = false; }
  }

  /** Limpa a mensagem e recarrega histórico/grupo após mudar o status. */
  private afterStatusChange() {
    this.staffMessage = '';
    const id = String(this.occurrence!.id);
    this.loadHistory(id);
    if (this.canUpdateStatus) this.loadGroup(id);
  }

  async updateToRejected() {
    if (!this.occurrence?.id) return;
    if (!this.rejectObservation.trim()) {
      this.toastr.warning('Informe a justificativa para indeferir a ocorrência.');
      return;
    }
    this.updating = true;
    try {
      await this.occurrenceEditService.updateStatus(
        String(this.occurrence.id), 'INDEFERIDA', this.rejectObservation.trim(), this.applyToGroup
      );
      this.occurrence!.status = 'INDEFERIDA';
      this.showRejectForm = false;
      this.toastr.success(this.applyToGroup
        ? `Grupo de ${this.groupSize} ocorrências indeferido.`
        : 'Ocorrência indeferida.');
      this.afterStatusChange();
    } catch { this.toastr.error('Erro ao indeferir ocorrência.'); }
    finally { this.updating = false; }
  }

  // Labels e cores compartilhados (domain/occurrence-labels)
  statusLabel = statusLabel;
  typeLabel   = typeLabel;
  typeColor   = typeColor;

  back() { this.router.navigate(['/occurrence/list']); }
}

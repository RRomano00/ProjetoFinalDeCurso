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
import { typeLabel, typeColor, statusLabel, statusColor, priorityLabel } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';
import { occurrencePopup } from '../../../domain/occurrence-popup';
import { AuthenticationService } from '../../../services/security/authentication.service';

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


  supportCount = 0;
  supportedByMe = false;
  supporting = false;
  showLoginPrompt = false;

  history: OccurrenceHistory[] = [];

  staffMessage = '';
  readonly presetMessages = [
    'Encaminhada para o setor responsável.',
    'Equipe designada para avaliação no local.',
    'Serviço agendado.',
    'Serviço executado e finalizado.'
  ];

  group: Occurrence[] = [];
  applyToGroup = false;
  get groupSize(): number { return this.group.length; }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceEditService: OccurrenceEditService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    public  auth: AuthenticationService,
    private toastr: ToastrService,
    private ngZone: NgZone
  ) {}

  async ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.router.navigate(['/occurrence/list']); return; }
    try {
      this.occurrence = await this.occurrenceReadService.findById(id);
      this.loadSupportInfo(id);
      this.loadHistory(id);
      await this.loadGroup(id);          // RF12: o mapa plota o grupo inteiro
      setTimeout(() => this.renderMap(), 0);
    } catch {
      this.toastr.error('Ocorrência não encontrada.');
      this.router.navigate(['/occurrence/list']);
    } finally { this.loading = false; }
  }

  private async loadSupportInfo(id: string) {
    try {
      const info = await this.occurrenceSupportService.getSupportInfo(id);
      this.supportCount  = info.count;
      this.supportedByMe = info.supportedByMe;
    } catch { /* silencioso: apenas não mostra o contador */ }
  }

  private async loadHistory(id: string) {
    try { this.history = await this.occurrenceReadService.getHistory(id); }
    catch { this.history = []; }
  }

  private async loadGroup(id: string) {
    try { this.group = await this.occurrenceReadService.getGroup(id); }
    catch { this.group = []; }
  }

  applyPreset(text: string) { this.staffMessage = text; }

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  /**
   * Apoia ou desfaz o apoio no mesmo botão. O estado vem do que o servidor
   * devolve (`supportedByMe`), nunca de um palpite local: assim a tela não
   * pode discordar do banco.
   */
  async toggleSupport() {
    if (this.auth.isVisitor()) { this.showLoginPrompt = true; return; }
    if (!this.occurrence?.id || this.supporting) return;
    const apoiando = this.supportedByMe;
    this.supporting = true;
    try {
      const info = await this.occurrenceSupportService.toggle(this.occurrence.id, apoiando);
      this.supportCount  = info.count;
      this.supportedByMe = info.supportedByMe;
      this.toastr[apoiando ? 'info' : 'success'](
        apoiando ? 'Apoio removido.' : 'Apoio registrado. Obrigado!');
    } catch {
      this.toastr.error(apoiando
        ? 'Não foi possível remover o apoio.'
        : 'Não foi possível registrar o apoio.');
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

      const points: [number, number][] = [];
      for (const g of this.group) {
        if (g.id === o.id || g.latitude == null || g.longitude == null) continue;
        const p: [number, number] = [g.latitude, g.longitude];
        points.push(p);
        L.marker(p, { icon: this.pointIcon(statusColor(g.status), false) }).addTo(this.map!)
          .bindPopup(occurrencePopup(g));
      }

      if (hasPoint) {
        points.push(center);
        // O popup não abre sozinho: o marcador maior com halo já diz qual é o desta
        // tela, e o balão aberto tapava justamente o mapa que se quer ver.
        L.marker(center, { icon: this.pointIcon(statusColor(o.status), true), zIndexOffset: 1000 })
          .addTo(this.map!)
          .bindPopup(occurrencePopup(o, { current: true }));
      }

      if (points.length > 1) this.map!.fitBounds(L.latLngBounds(points).pad(0.4));
    });
  }

  /** O ponto da ocorrência aberta vem maior e com halo, para não sumir no grupo. */
  private pointIcon(color: string, current: boolean): L.DivIcon {
    const size = current ? 22 : 13;
    return L.divIcon({
      className: '',
      html: `<div style="width:${size}px;height:${size}px;background:${color};
             border:${current ? 3 : 2}px solid #fff;border-radius:50%;
             box-shadow:0 0 0 ${current ? 5 : 0}px ${color}4d, 0 1px 4px rgba(0,0,0,.4)"></div>`,
      iconSize: [size, size], iconAnchor: [size / 2, size / 2]
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

  private afterStatusChange() {
    this.staffMessage = '';
    const id = String(this.occurrence!.id);
    this.loadHistory(id);
    this.loadGroup(id);
  }

  async sendReply() {
    if (!this.occurrence?.id) return;
    if (!this.staffMessage.trim()) {
      this.toastr.warning('Escreva a mensagem antes de enviar.');
      return;
    }
    this.updating = true;
    try {
      // Reenvia o status atual: entra no histórico como mensagem e notifica o autor.
      await this.occurrenceEditService.updateStatus(
        String(this.occurrence.id), this.occurrence.status, this.staffMessage.trim(), this.applyToGroup);
      this.toastr.success('Resposta enviada ao cidadão.');
      this.afterStatusChange();
    } catch { this.toastr.error('Erro ao enviar a resposta.'); }
    finally { this.updating = false; }
  }

  get canReopen(): boolean {
    return this.auth.isStaff()
        && (this.occurrence?.status === 'ATENDIDA' || this.occurrence?.status === 'INDEFERIDA');
  }

  async reopen() {
    if (!this.occurrence?.id) return;
    this.updating = true;
    try {
      await this.occurrenceEditService.updateStatus(
        String(this.occurrence.id), 'EM_ANDAMENTO', this.staffMessage.trim() || undefined, this.applyToGroup);
      this.occurrence!.status = 'EM_ANDAMENTO';
      this.toastr.success(this.applyToGroup
        ? `Grupo de ${this.groupSize} ocorrências reaberto.`
        : 'Ocorrência reaberta.');
      this.afterStatusChange();
    } catch { this.toastr.error('Erro ao reabrir a ocorrência.'); }
    finally { this.updating = false; }
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

  statusLabel = statusLabel;
  typeLabel   = typeLabel;
  typeColor   = typeColor;
  priorityLabel = priorityLabel;

  back() { this.router.navigate(['/occurrence/list']); }
}

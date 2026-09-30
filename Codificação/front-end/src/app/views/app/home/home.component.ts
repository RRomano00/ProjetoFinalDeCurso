import { Component, OnInit, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { OccurrenceSupportService, SupportInfo } from '../../../services/occurrence-support.service';
import { GeocodingService } from '../../../services/local/geocoding.service';
import { LocalityPreferenceService, Municipality } from '../../../services/local/locality-preference.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { CopyProtocolComponent } from '../../../shared/copy-protocol.component';
import { openOccurrence } from '../../../shared/open-occurrence';
import { statusLabel, statusColor, typeLabel, typeColor, OCCURRENCE_TYPES } from '../../../domain/occurrence-labels';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';
import { occurrencePopup } from '../../../domain/occurrence-popup';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';
import { persistFilters } from '../../../shared/persist-filters';

interface MapGroup {
  hub?: L.Marker;
  members: { o: Occurrence; marker: L.Marker; real: L.LatLng }[];
  expanded: boolean;
}

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule, FormsModule, CopyProtocolComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  username   = '';
  occurrences: Occurrence[] = [];
  mapReady   = false;
  loadingMap = false;

  private map!: L.Map;
  private markers: L.Marker[] = [];
  private markerById = new Map<number, L.Marker>();
  // Agrupadas (mesmo problema a até 70 m) viram um ponto só, que abre em leque ao clicar.
  private groups = new Map<number, MapGroup>();
  private supportInfo = new Map<number, SupportInfo>();
  private supportingId: number | null = null;

  showLoginPrompt = false;

  private coordsCache = new Map<number, { lat: number; lng: number }>();

  filterCity = '';
  municipalityOptions: Municipality[] = [];
  municipalityLabel = LocalityPreferenceService.label;
  cityKey           = LocalityPreferenceService.fold;

  mapFilterNeighborhood = '';
  mapFilterType         = '';
  mapFilterStatus       = '';
  mapStatusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'INDEFERIDA'];

  get totalOccurrences() { return this.inCity.length; }

  get selectedMunicipality(): Municipality | null {
    return this.municipalityOptions.find(
      m => LocalityPreferenceService.fold(m.city) === this.filterCity) || null;
  }

  get inCity(): Occurrence[] {
    const chosen = this.selectedMunicipality;
    return this.occurrences.filter(o => LocalityPreferenceService.matches(chosen, o.city, o.state));
  }

  get recent(): Occurrence[] { return this.inCity.slice(0, 5); }

  private static readonly PLURAL: Record<string, string> = {
    PENDENTE:     'pendentes',
    EM_ANDAMENTO: 'em andamento',
    CONCLUIDA:    'concluídas',
    INDEFERIDA:   'indeferidas',
  };

  get statusBreakdown() {
    return this.mapStatusOptions
      .map(status => ({
        status,
        count: this.inCity.filter(o => o.status === status).length,
        color: statusColor(status),
        one:   statusLabel(status).toLowerCase(),
        many:  HomeComponent.PLURAL[status],
      }))
      .filter(s => s.count > 0);
  }

  statusLabel = statusLabel;
  typeLabel   = typeLabel;
  typeColor   = typeColor;

  get mapNeighborhoodOptions(): string[] {
    const set = new Set<string>();
    this.inCity.forEach(o => { if (o.neighborhood?.trim()) set.add(o.neighborhood.trim()); });
    return Array.from(set).sort();
  }

  get mapTypeOptions(): { value: string; label: string }[] {
    const present = new Set(this.inCity.map(o => o.type).filter(Boolean));
    return OCCURRENCE_TYPES.filter(t => present.has(t.value));
  }

  get hasMapFilters(): boolean {
    return !!(this.filterCity || this.mapFilterNeighborhood || this.mapFilterType || this.mapFilterStatus);
  }

  private get filteredForMap(): Occurrence[] {
    return this.inCity.filter(o =>
      (!this.mapFilterNeighborhood || o.neighborhood?.trim() === this.mapFilterNeighborhood) &&
      (!this.mapFilterType         || o.type === this.mapFilterType) &&
      (!this.mapFilterStatus       || o.status === this.mapFilterStatus)
    );
  }

  clearMapFilters() {
    if (!this.auth.isAnonymous()) this.filterCity = '';  // visitante troca pelo "Alterar município"
    this.mapFilterNeighborhood = '';
    this.mapFilterType = '';
    this.mapFilterStatus = '';
    this.onCityChange();
  }

  async onCityChange() {
    this.locality.choice = this.selectedMunicipality;
    await this.refreshMarkers();
    await this.frameSelection();
  }

  constructor(
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    private locality: LocalityPreferenceService,
    public  auth: AuthenticationService,
    private router: Router,
    private toastr: ToastrService,
    private ngZone: NgZone
  ) {
    // O município já é lembrado pelo LocalityPreferenceService.
    persistFilters(this, 'home-map', ['mapFilterNeighborhood', 'mapFilterType', 'mapFilterStatus']);
    // Filtro salvo antes, quando a Finalizada ainda era opção aqui.
    if (!this.mapStatusOptions.includes(this.mapFilterStatus)) this.mapFilterStatus = '';
  }

  openCard(o: Occurrence, e: Event) { openOccurrence(this.router, o.id, e); }

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  locating = false;

  async goToMyLocation() {
    this.locating = true;
    try {
      const position = await this.locality.position();
      if (!position) return;
      const { latitude, longitude } = position.coords;
      this.ngZone.run(() => {
        this.showMe(latitude, longitude);
        this.map.setView([latitude, longitude], 17);
      });
    } finally {
      this.ngZone.run(() => this.locating = false);
    }
  }

  ngOnInit() {
    this.username = localStorage.getItem('fullname') || 'Visitante';
  }

  async ngAfterViewInit() {
    setTimeout(async () => {
      this.initMap();
      await this.loadOccurrencesAndPlot();
    }, 0);
  }

  ngOnDestroy() {
    this.stopGps();
    if (this.gpsPermission) this.gpsPermission.onchange = null;
    if (this.map) this.map.remove();
  }

  private initMap() {
    this.map = L.map('incident-map', { zoomControl: true })
      .setView([SANTA_RITA_DO_SAPUCAI.lat, SANTA_RITA_DO_SAPUCAI.lng], DEFAULT_MAP_ZOOM);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors', maxZoom: 19
    }).addTo(this.map);
    setTimeout(() => this.map.invalidateSize(), 100);
    // O leque é medido em pixels: refaz ao mudar o zoom.
    this.map.on('zoomend', () => this.groups.forEach(g => { if (g.expanded) this.spread(g, false); }));

    this.map.getContainer().addEventListener('click', (ev: Event) => {
      const alvo = (ev.target as HTMLElement | null)?.closest('[data-occ-support]');
      if (!alvo) return;
      ev.preventDefault();
      const id = Number((alvo as HTMLElement).dataset['occSupport']);
      if (Number.isFinite(id)) this.ngZone.run(() => this.toggleSupportFromMap(id));
    });

    this.mapReady = true;
  }

  private async loadOccurrencesAndPlot() {
    try {
      // Finalizada (excluída) só aparece na listagem do Super Administrador, nunca aqui nem no mapa.
      this.occurrences = (await this.occurrenceReadService.findAll() || [])
        .filter(o => o.status !== 'FINALIZADA');
    } catch {
      this.occurrences = [];
    }

    const conhecido = await this.locality.startingMunicipality(this.auth.anonymousMunicipality());

    this.municipalityOptions = LocalityPreferenceService.options(this.occurrences, [conhecido]);
    if (conhecido) this.filterCity = LocalityPreferenceService.fold(conhecido.city);

    await this.refreshMarkers();
    await this.frameSelection();

    void this.followGps();
  }

  private gpsWatch?: number;
  private gpsOn = false;
  private gpsPermission?: PermissionStatus;

  /**
   * O mapa abre no município da conta; quando o GPS passa a responder — já ligado ao
   * abrir ou ligado depois, a qualquer momento — ele vai para a posição do aparelho.
   * watchPosition continua escutando com o GPS desligado e dispara quando ele volta.
   */
  private async followGps() {
    if (!navigator.geolocation || this.gpsWatch !== undefined) return;
    this.gpsWatch = navigator.geolocation.watchPosition(
      position => {
        this.showMe(position.coords.latitude, position.coords.longitude);
        if (!this.gpsOn) { this.gpsOn = true; void this.goToGps(position); }
      },
      // Timeout é passageiro; só desligar o GPS ou negar a permissão "rearma" o redirecionamento.
      error => { if (error.code !== error.TIMEOUT) { this.gpsOn = false; this.hideMe(); } },
      { enableHighAccuracy: false, maximumAge: 60000 });

    // Permissão negada encerra a escuta; se liberarem depois nas configurações, recomeça.
    try {
      this.gpsPermission = await navigator.permissions?.query({ name: 'geolocation' as PermissionName });
      if (this.gpsPermission) this.gpsPermission.onchange = () => {
        if (this.gpsPermission?.state !== 'granted') return;
        this.stopGps();
        void this.followGps();
      };
    } catch { }
  }

  private stopGps() {
    if (this.gpsWatch !== undefined) navigator.geolocation.clearWatch(this.gpsWatch);
    this.gpsWatch = undefined;
    this.gpsOn = false;
    this.hideMe();
  }

  private meMarker?: L.Marker;

  // Pessoa na posição do aparelho enquanto o GPS responde; some quando ele é desligado.
  private showMe(lat: number, lng: number) {
    if (!this.map) return;
    if (this.meMarker) { this.meMarker.setLatLng([lat, lng]); return; }
    const icon = L.divIcon({
      className: '',
      html: `<div style="width:30px;height:30px;border-radius:50%;background:#14487e;border:2px solid #fff;
             box-shadow:0 0 0 6px rgb(20 72 126 / .22), 0 1px 4px rgb(18 32 51 / .45);
             display:flex;align-items:center;justify-content:center">
             <svg width="16" height="16" viewBox="0 0 24 24" fill="#fff" aria-hidden="true">
               <circle cx="12" cy="6.5" r="4"/><path d="M4 22a8 8 0 0 1 16 0z"/></svg></div>`,
      iconSize: [30, 30], iconAnchor: [15, 15]
    });
    this.meMarker = L.marker([lat, lng], { icon, title: 'Você está aqui', keyboard: false, zIndexOffset: 2000 })
      .addTo(this.map);
  }

  private hideMe() {
    this.meMarker?.remove();
    this.meMarker = undefined;
  }

  private async goToGps(position: GeolocationPosition) {
    const { latitude, longitude } = position.coords;
    if (this.auth.isAnonymous()) return;  // o mapa do visitante fica no município que ele escolheu
    const municipio = await this.locality.municipalityAt(latitude, longitude);
    await this.ngZone.run(async () => {
      if (municipio && LocalityPreferenceService.fold(municipio.city) !== this.filterCity) {
        this.municipalityOptions = LocalityPreferenceService.options(
          this.occurrences, [municipio, ...this.municipalityOptions]);
        this.filterCity = LocalityPreferenceService.fold(municipio.city);
        await this.refreshMarkers();
      }
      this.map?.setView([latitude, longitude], 15);
    });
  }

  private async frameSelection() {
    if (!this.map) return;
    if (this.markers.length) {
      this.map.fitBounds(L.latLngBounds(this.markers.map(m => m.getLatLng())),
                         { padding: [40, 40], maxZoom: DEFAULT_MAP_ZOOM });
      return;
    }
    const center = await this.locality.mapCenter();
    if (center) this.map.setView([center.lat, center.lng], center.zoom);
  }

  private static readonly MAX_GEOCODE = 30;

  async refreshMarkers() {
    this.loadingMap = true;
    this.markers.forEach(m => m.remove());
    this.markers = [];
    this.markerById.clear();
    this.groups.forEach(g => g.hub?.remove());
    this.groups.clear();

    // Com coordenadas entram todas; sem elas, só as 30 mais recentes, porque cada
    // geocodificação espera ~1 s (limite do Nominatim).
    const pendentes: Occurrence[] = [];
    for (const o of this.filteredForMap) {
      const coords = this.knownCoords(o);
      if (coords) this.plot(o, coords);
      else if (pendentes.length < HomeComponent.MAX_GEOCODE) pendentes.push(o);
    }

    for (const o of pendentes) {
      const geo = await this.geocodingService.geocode(o.street!, o.neighborhood!, o.city!);
      if (geo) this.plot(o, geo);
      await this.delay(1100);
    }
    this.loadingMap = false;
  }

  private knownCoords(o: Occurrence): { lat: number; lng: number } | null {
    const cached = o.id != null ? this.coordsCache.get(o.id) : undefined;
    if (cached) return cached;
    return o.latitude && o.longitude ? { lat: o.latitude, lng: o.longitude } : null;
  }

  private plot(o: Occurrence, coords: { lat: number; lng: number }) {
    if (o.id != null) this.coordsCache.set(o.id, coords);
    this.ngZone.run(() => this.addMarker(o, coords.lat, coords.lng));
  }

  private addMarker(o: Occurrence, lat: number, lng: number) {
    const color = statusColor(o.status);
    const icon = L.divIcon({
      className: '',
      // Ponto de 14px dentro de uma área de toque de 32px.
      html: `<div style="width:32px;height:32px;display:flex;align-items:center;justify-content:center">
             <div style="width:14px;height:14px;background:${color};border:2px solid #fff;
             border-radius:50%;box-shadow:0 1px 3px rgb(18 32 51 / .45)"></div></div>`,
      iconSize: [32, 32], iconAnchor: [16, 16]
    });

    const marker = L.marker([lat, lng], { icon }).bindPopup(this.popupHtml(o));
    this.markers.push(marker);
    if (o.id != null) this.markerById.set(o.id, marker);

    const key = o.groupId ?? o.id ?? -this.markers.length;
    let g = this.groups.get(key);
    if (!g) this.groups.set(key, g = { members: [], expanded: false });
    g.members.push({ o, marker, real: L.latLng(lat, lng) });
    this.renderGroup(g);

    marker.on('popupopen', async () => {
      if (o.id == null) return;
      try {
        this.supportInfo.set(o.id, await this.occurrenceSupportService.getSupportInfo(o.id));
        marker.setPopupContent(this.popupHtml(o));
      } catch { }
    });
  }

  private renderGroup(g: MapGroup) {
    if (g.members.length === 1) { g.members[0].marker.addTo(this.map); return; }
    const center = L.latLngBounds(g.members.map(m => m.real)).getCenter();
    if (!g.hub) {
      g.hub = L.marker(center).on('click', () => this.toggleGroup(g));
    }
    g.hub.setLatLng(center).setIcon(this.hubIcon(g)).addTo(this.map);
    g.hub.getElement()?.setAttribute('title', `${g.members.length} ocorrências agrupadas: clique para `
                                               + (g.expanded ? 'juntar' : 'ver cada uma'));
    if (g.expanded) this.spread(g, false);
    else g.members.forEach(m => m.marker.remove());
  }

  private toggleGroup(g: MapGroup) {
    g.expanded = !g.expanded;
    g.hub!.setOpacity(g.expanded ? 0.45 : 1);
    g.hub!.getElement()?.setAttribute('title', `${g.members.length} ocorrências agrupadas: clique para `
                                                + (g.expanded ? 'juntar' : 'ver cada uma'));
    if (g.expanded) { this.spread(g, true); return; }

    const hub = g.hub!.getLatLng();
    g.members.forEach(m => { this.animated(m.marker); m.marker.setLatLng(hub); });
    setTimeout(() => {
      if (g.expanded) return;  // reabriu no meio da animação
      g.members.forEach(m => m.marker.remove().setLatLng(m.real).setZIndexOffset(0));
    }, HomeComponent.SPREAD_MS);
  }

  private static readonly SPREAD_MS = 280;

  // Os pontos ficam a até 70 m um do outro, sobrepostos no zoom da cidade: abrem num círculo em volta do centro.
  private spread(g: MapGroup, animate: boolean) {
    const hub = g.hub!.getLatLng();
    const c = this.map.latLngToLayerPoint(hub);
    const n = g.members.length;
    const r = Math.max(36, n * 8);
    g.members.forEach((m, i) => {
      const a = -Math.PI / 2 + (2 * Math.PI * i) / n;
      const target = this.map.layerPointToLatLng(c.add(L.point(r * Math.cos(a), r * Math.sin(a))));
      m.marker.setZIndexOffset(1000);
      if (!animate) { m.marker.setLatLng(target).addTo(this.map); return; }
      m.marker.setLatLng(hub).addTo(this.map);
      this.animated(m.marker);
      m.marker.setLatLng(target);
    });
  }

  // Liga a transição só durante o abrir/fechar, para não atrasar os pontos no arrastar/zoom.
  private animated(marker: L.Marker) {
    const el = marker.getElement();
    if (!el) return;
    el.classList.add('occ-spread');
    void el.offsetWidth;  // aplica a posição atual antes de mudar, senão não anima
    setTimeout(() => el.classList.remove('occ-spread'), HomeComponent.SPREAD_MS + 40);
  }

  // O mesmo ponto de status do mapa, com borda colorida por fora e o total em cima.
  // Cor: o status mais frequente no grupo.
  private hubIcon(g: MapGroup): L.DivIcon {
    const counts = new Map<string, number>();
    g.members.forEach(m => counts.set(m.o.status, (counts.get(m.o.status) || 0) + 1));
    const color = statusColor([...counts].sort((a, b) => b[1] - a[1])[0][0]);
    return L.divIcon({
      className: '',
      html: `<div style="position:relative;width:34px;height:34px;display:flex;align-items:center;justify-content:center">
             <span style="width:20px;height:20px;box-sizing:border-box;border-radius:50%;background:${color};
             border:2.5px solid #fff;box-shadow:0 0 0 3px ${color}, 0 2px 5px rgb(18 32 51 / .45)"></span>
             <span style="position:absolute;left:19px;top:-1px;min-width:16px;height:16px;padding:0 4px;
             box-sizing:border-box;border-radius:8px;background:#122033;color:#fff;box-shadow:0 0 0 1.5px #fff;
             font:700 10px/16px var(--font);text-align:center">${g.members.length}</span></div>`,
      iconSize: [34, 34], iconAnchor: [17, 17]
    });
  }

  private refreshPopup(o: Occurrence) {
    const marker = o.id != null ? this.markerById.get(o.id) : undefined;
    if (marker) marker.setPopupContent(this.popupHtml(o));
  }

  private async toggleSupportFromMap(id: number) {
    if (this.auth.isVisitor()) { this.showLoginPrompt = true; return; }
    if (this.supportingId != null) return;
    const o = this.occurrences.find(x => x.id === id);
    if (!o) return;
    const apoiando = !!this.supportInfo.get(id)?.supportedByMe;
    this.supportingId = id;
    this.refreshPopup(o);
    try {
      this.supportInfo.set(id, await this.occurrenceSupportService.toggle(id, apoiando));
      this.toastr[apoiando ? 'info' : 'success'](
        apoiando ? 'Apoio removido.' : 'Apoio registrado. Obrigado!');
    } catch {
      this.toastr.error(apoiando
        ? 'Não foi possível remover o apoio.'
        : 'Não foi possível registrar o apoio.');
    } finally {
      this.supportingId = null;
      this.refreshPopup(o);
    }
  }

  private popupHtml(o: Occurrence): string {
    const info     = o.id != null ? this.supportInfo.get(o.id) : undefined;
    const salvando = this.supportingId === o.id;
    const apoiado  = !!info?.supportedByMe;

    const botao = this.auth.canSupport() ? `
      <button type="button" class="btn-support btn-support--sm occ-popup__support${apoiado ? ' supported' : ''}"
              data-occ-support="${o.id}"${salvando ? ' disabled' : ''}
              aria-label="${apoiado
                ? 'Você apoia esta ocorrência; clique para remover o apoio'
                : 'Apoiar esta ocorrência'}">
        <span class="btn-support__state">${salvando ? 'Salvando<span class="loading-dots" aria-hidden="true"></span>' : (apoiado ? '✓ Você apoia' : '🤝 Apoiar')}</span>
        <span class="btn-support__undo">Não apoiar</span>
      </button>` : '';

    return occurrencePopup(o, { supportCount: info?.count, footer: botao });
  }

  private delay(ms: number) { return new Promise(r => setTimeout(r, ms)); }
}

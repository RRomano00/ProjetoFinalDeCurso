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
import { statusLabel, statusColor, typeLabel, typeColor, OCCURRENCE_TYPES } from '../../../domain/occurrence-labels';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';
import { occurrencePopup } from '../../../domain/occurrence-popup';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule, FormsModule],
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
  readonly mapStatusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'INDEFERIDA'];

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
    return ['PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'INDEFERIDA']
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
    this.filterCity = '';
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
  ) {}

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  locating = false;

  async goToMyLocation() {
    this.locating = true;
    try {
      const position = await this.locality.position();
      if (!position) return;
      const { latitude, longitude } = position.coords;
      this.ngZone.run(() => this.map.setView([latitude, longitude], 17));
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

  ngOnDestroy() { if (this.map) this.map.remove(); }

  private initMap() {
    this.map = L.map('incident-map', { zoomControl: true })
      .setView([SANTA_RITA_DO_SAPUCAI.lat, SANTA_RITA_DO_SAPUCAI.lng], DEFAULT_MAP_ZOOM);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors', maxZoom: 19
    }).addTo(this.map);
    setTimeout(() => this.map.invalidateSize(), 100);

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
      this.occurrences = await this.occurrenceReadService.findAll() || [];
    } catch {
      this.occurrences = [];
    }

    const conhecido = this.locality.choice ?? await this.locality.ofCurrentUser();

    this.municipalityOptions = LocalityPreferenceService.options(this.occurrences, [conhecido]);
    if (conhecido) this.filterCity = LocalityPreferenceService.fold(conhecido.city);

    await this.refreshMarkers();
    await this.frameSelection();

    void this.seguirGps(conhecido);
  }

  private async seguirGps(conhecido: Municipality | null) {
    const detectado = await this.locality.ensure();
    if (!detectado) return;
    const mesmo = conhecido && LocalityPreferenceService.fold(conhecido.city)
                            === LocalityPreferenceService.fold(detectado.city);
    if (mesmo) return;

    this.municipalityOptions = LocalityPreferenceService.options(
      this.occurrences, [detectado, conhecido]);
    this.filterCity = LocalityPreferenceService.fold(detectado.city);
    await this.refreshMarkers();
    await this.frameSelection();
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

  async refreshMarkers() {
    this.loadingMap = true;
    this.markers.forEach(m => m.remove());
    this.markers = [];
    this.markerById.clear();

    const pendentes: Occurrence[] = [];
    for (const o of this.filteredForMap.slice(0, 20)) {
      const coords = this.knownCoords(o);
      if (coords) this.plot(o, coords);
      else pendentes.push(o);
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
      html: `<div style="width:14px;height:14px;background:${color};border:2px solid #fff;
             border-radius:50%;box-shadow:0 1px 3px rgb(18 32 51 / .45)"></div>`,
      iconSize: [14, 14], iconAnchor: [7, 7]
    });

    const marker = L.marker([lat, lng], { icon }).addTo(this.map)
      .bindPopup(this.popupHtml(o));
    this.markers.push(marker);
    if (o.id != null) this.markerById.set(o.id, marker);

    marker.on('popupopen', async () => {
      if (o.id == null) return;
      try {
        this.supportInfo.set(o.id, await this.occurrenceSupportService.getSupportInfo(o.id));
        marker.setPopupContent(this.popupHtml(o));
      } catch { }
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
        <span class="btn-support__state">${salvando ? 'Salvando...' : (apoiado ? '✓ Você apoia' : '🤝 Apoiar')}</span>
        <span class="btn-support__undo">Não apoiar</span>
      </button>` : '';

    return occurrencePopup(o, { supportCount: info?.count, footer: botao });
  }

  private delay(ms: number) { return new Promise(r => setTimeout(r, ms)); }
}

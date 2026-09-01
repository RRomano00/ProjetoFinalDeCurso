import { Component, OnInit, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { OccurrenceReadService } from '../../../services/occurrence-read.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
import { GeocodingService } from '../../../services/local/geocoding.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { statusLabel, statusColor, typeLabel, OCCURRENCE_TYPES } from '../../../domain/occurrence-labels';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';

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
  /** Coordenadas já resolvidas por ocorrência (evita re-geocodificar ao filtrar). */
  private coordsCache = new Map<number, { lat: number; lng: number }>();

  // RF21: filtros do mapa por bairro, categoria e status
  mapFilterNeighborhood = '';
  mapFilterType         = '';
  mapFilterStatus       = '';
  readonly mapStatusOptions = ['PENDENTE', 'EM_ANDAMENTO', 'ATENDIDA', 'INDEFERIDA'];

  get totalOccurrences() { return this.occurrences.length; }
  get resolvedCount()    { return this.occurrences.filter(o => o.status === 'ATENDIDA').length; }
  get pendingCount()     { return this.occurrences.filter(o => o.status === 'PENDENTE').length; }

  // Labels compartilhados (domain/occurrence-labels)
  statusLabel = statusLabel;
  typeLabel   = typeLabel;

  /** Bairros presentes nas ocorrências carregadas. */
  get mapNeighborhoodOptions(): string[] {
    const set = new Set<string>();
    this.occurrences.forEach(o => { if (o.neighborhood?.trim()) set.add(o.neighborhood.trim()); });
    return Array.from(set).sort();
  }

  /** Categorias presentes nas ocorrências carregadas. */
  get mapTypeOptions(): { value: string; label: string }[] {
    const present = new Set(this.occurrences.map(o => o.type).filter(Boolean));
    return OCCURRENCE_TYPES.filter(t => present.has(t.value));
  }

  get hasMapFilters(): boolean {
    return !!(this.mapFilterNeighborhood || this.mapFilterType || this.mapFilterStatus);
  }

  /** Ocorrências que passam nos filtros do mapa (RF21). */
  private get filteredForMap(): Occurrence[] {
    return this.occurrences.filter(o =>
      (!this.mapFilterNeighborhood || o.neighborhood?.trim() === this.mapFilterNeighborhood) &&
      (!this.mapFilterType         || o.type === this.mapFilterType) &&
      (!this.mapFilterStatus       || o.status === this.mapFilterStatus)
    );
  }

  clearMapFilters() {
    this.mapFilterNeighborhood = '';
    this.mapFilterType = '';
    this.mapFilterStatus = '';
    this.refreshMarkers();
  }

  constructor(
    private occurrenceReadService: OccurrenceReadService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    private ngZone: NgZone
  ) {}

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
    this.mapReady = true;
  }

  private async loadOccurrencesAndPlot() {
    try {
      this.occurrences = await this.occurrenceReadService.findAll() || [];
    } catch {
      this.occurrences = [];
    }
    await this.refreshMarkers();
  }

  /** RF21: redesenha os marcadores conforme os filtros (coordenadas ficam em cache). */
  async refreshMarkers() {
    this.loadingMap = true;
    this.markers.forEach(m => m.remove());
    this.markers = [];

    for (const o of this.filteredForMap.slice(0, 20)) {
      let coords = o.id != null ? this.coordsCache.get(o.id) : undefined;

      if (!coords && o.latitude && o.longitude) {
        coords = { lat: o.latitude, lng: o.longitude };
      } else if (!coords) {
        const geo = await this.geocodingService.geocode(o.street!, o.neighborhood!, o.city!);
        if (geo) coords = geo;
        await this.delay(1100);   // respeita o rate limit do Nominatim
      }

      if (coords) {
        if (o.id != null) this.coordsCache.set(o.id, coords);
        const c = coords;
        this.ngZone.run(() => this.addMarker(o, c.lat, c.lng));
      }
    }
    this.loadingMap = false;
  }

  /** Adiciona o marcador da ocorrência no mapa, com cor por status e popup resumido. */
  private addMarker(o: Occurrence, lat: number, lng: number) {
    const color = statusColor(o.status);
    const icon = L.divIcon({
      className: '',
      html: `<div style="width:14px;height:14px;background:${color};border:2px solid #fff;
             border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.4)"></div>`,
      iconSize: [14, 14], iconAnchor: [7, 7]
    });

    const marker = L.marker([lat, lng], { icon }).addTo(this.map)
      .bindPopup(this.popupHtml(o, color, null));
    this.markers.push(marker);

    // Busca a quantidade de apoios só quando o popup é aberto (evita 1 request por marcador)
    marker.on('popupopen', async () => {
      if (!o.id) return;
      try {
        const info = await this.occurrenceSupportService.getSupportInfo(o.id);
        marker.setPopupContent(this.popupHtml(o, color, info.count));
      } catch { /* mantém o popup sem o contador */ }
    });
  }

  /** Monta o HTML do popup do marcador (com contador de apoios, se já carregado). */
  private popupHtml(o: Occurrence, color: string, supportCount: number | null): string {
    const supports = supportCount == null ? '' :
      `<span style="background:#eff6ff;color:#1e40af;padding:2px 8px;border-radius:99px;font-size:11px;font-weight:600;margin-left:4px">
        🤝 ${supportCount} ${supportCount === 1 ? 'apoio' : 'apoios'}
      </span>`;
    return `<div style="font-family:sans-serif;font-size:13px;min-width:180px">
      <b>${(o.title || o.description || '').slice(0, 60)}</b><br>
      <span style="color:#6b7280">${o.street || ''}, ${o.neighborhood || ''}</span><br>
      <span style="background:${color}22;color:${color};padding:2px 8px;border-radius:99px;font-size:11px;font-weight:600">
        ${statusLabel(o.status)}
      </span>${supports}</div>`;
  }

  private delay(ms: number) { return new Promise(r => setTimeout(r, ms)); }
}

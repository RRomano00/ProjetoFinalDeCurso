import { Component, OnInit, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';
import { OccurrenceCreateService } from '../../../services/occurrence-create.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
import { GeocodingService } from '../../../services/local/geocoding.service';
import { LocalityService, CityOptions } from '../../../services/local/locality.service';
import { LocalityPreferenceService } from '../../../services/local/locality-preference.service';
import { OccurrenceCoverageService } from '../../../services/occurrence-coverage.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { OCCURRENCE_TYPES } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { merge } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { SANTA_RITA_DO_SAPUCAI, DEFAULT_MAP_ZOOM } from '../../../domain/map.constants';

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

@Component({
  selector: 'app-create-occurrence',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './create-occurrence.component.html',
  styleUrl: './create-occurrence.component.css'
})
export class CreateOccurrenceComponent implements OnInit, AfterViewInit, OnDestroy {
  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  cityServed: boolean | null = null;

  form!: FormGroup;
  loading = false;
  trackingCode: string | null = null;

  geocodeStatus: 'idle' | 'loading' | 'success' | 'partial' | 'error' = 'idle';
  locating = false;
  private map!: L.Map;
  private marker?: L.Marker;

  readonly MAX_PHOTOS = 3;
  photos: { url: string; cloudinaryPublicId: string; imageBlurred: boolean }[] = [];
  photoState: 'idle' | 'uploading' | 'rejected' | 'error' = 'idle';
  photoMessage = '';

  nearbyDuplicates: Occurrence[] = [];
  checkingDuplicates = false;
  supportedIds = new Set<number>();
  supportingId: number | null = null;
  showLoginPrompt = false;

  occurrenceTypes = OCCURRENCE_TYPES;

  constructor(
    private occurrenceCreateService: OccurrenceCreateService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    private fb: FormBuilder,
    private router: Router,
    public  auth: AuthenticationService,
    private toastr: ToastrService,
    private ngZone: NgZone,
    private locality: LocalityService,
    private localityPreference: LocalityPreferenceService,
    private coverage: OccurrenceCoverageService
  ) {}

  ngOnInit() {
    this.form = this.fb.group({
      title:            ['', [Validators.required, Validators.minLength(5), Validators.maxLength(120)]],
      description:      ['', [Validators.required, Validators.minLength(10)]],
      type:             ['', Validators.required],
      street:           ['', Validators.required],
      number:           [''],
      neighborhood:     ['', Validators.required],
      addressReference: [''],
      state:            ['MG', Validators.required],
      city:             ['Santa Rita do Sapucaí', Validators.required],
      latitude:         [null],
      longitude:        [null],
      anonymous:        [false]
    });

    if (this.auth.isVisitor()) this.form.patchValue({ anonymous: true });

    this.form.get('type')!.valueChanges.subscribe(() => this.checkDuplicates());

    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);

    this.form.get('city')!.valueChanges.subscribe(() => this.checkCoverage());
    this.form.get('state')!.valueChanges.subscribe(() => this.checkCoverage());
    this.checkCoverage();

    merge(this.form.get('street')!.valueChanges,
          this.form.get('neighborhood')!.valueChanges,
          this.form.get('city')!.valueChanges,
          this.form.get('state')!.valueChanges)
      .pipe(debounceTime(900))
      .subscribe(() => this.centerOnTypedAddress());

    this.loadMySupports();
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.initMap();
      this.requestCurrentLocation(true);
    }, 0);
  }

  ngOnDestroy() { if (this.map) this.map.remove(); }

  private initMap() {
    this.map = L.map('create-map', { zoomControl: true })
      .setView([SANTA_RITA_DO_SAPUCAI.lat, SANTA_RITA_DO_SAPUCAI.lng], DEFAULT_MAP_ZOOM);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors', maxZoom: 19
    }).addTo(this.map);
    setTimeout(() => this.map.invalidateSize(), 100);

    this.map.on('click', (e: L.LeafletMouseEvent) => {
      this.ngZone.run(() => this.setLocation(e.latlng.lat, e.latlng.lng, true));
    });
  }

  async requestCurrentLocation(centrarNoMunicipio = false) {
    if (centrarNoMunicipio) {
      const center = await this.localityPreference.mapCenter();
      if (center) this.ngZone.run(() => this.map.setView([center.lat, center.lng], center.zoom));
    }

    this.locating = true;
    try {
      const position = await this.localityPreference.position();
      if (!position) return;
      this.ngZone.run(() => {
        const { latitude, longitude } = position.coords;
        this.map.setView([latitude, longitude], 17);
        this.setLocation(latitude, longitude, true);
      });
    } finally {
      this.ngZone.run(() => this.locating = false);
    }
  }

  private async centerOnTypedAddress() {
    if (!this.map) return;
    const { street, neighborhood, city, state } = this.form.value;
    if (!city) return;

    const local = [city, state].filter(Boolean).join(', ');
    const busca = [street, neighborhood, local].filter(Boolean).join(' | ');
    if (busca === this.lastCentered || busca === this.addressFromMap) return;
    this.lastCentered = busca;

    const coords = await this.geocodingService.geocode(street || '', neighborhood || '', local);
    if (coords) this.ngZone.run(() => this.map.setView([coords.lat, coords.lng], street ? 16 : 13));
  }

  private lastCentered  = '';
  private addressFromMap = '';

  private async setLocation(lat: number, lng: number, fillAddress: boolean) {
    this.form.patchValue({ latitude: lat, longitude: lng });
    this.checkDuplicates();

    if (this.marker) this.marker.setLatLng([lat, lng]);
    else this.marker = L.marker([lat, lng]).addTo(this.map);

    if (!fillAddress) return;

    this.geocodeStatus = 'loading';
    const addr = await this.geocodingService.reverseGeocode(lat, lng);
    this.ngZone.run(() => {
      if (addr) {
        const uf = this.locality.normalizeUf(addr.state);
        if (uf && uf !== this.form.value.state) this.form.patchValue({ state: uf });
        this.form.patchValue({
          street:       addr.street       || this.form.value.street,
          neighborhood: addr.neighborhood || this.form.value.neighborhood,
          city:         addr.city         || this.form.value.city
        });
        const v = this.form.value;
        this.addressFromMap =
          [v.street, v.neighborhood, [v.city, v.state].filter(Boolean).join(', ')]
            .filter(Boolean).join(' | ');
        this.geocodeStatus = addr.street ? 'success' : 'partial';
      } else {
        this.geocodeStatus = 'error';
      }
    });
  }

  private async checkCoverage() {
    const { city, state } = this.form.value;
    this.cityServed = await this.coverage.isServed(city, state);
  }

  async checkDuplicates() {
    const { latitude, longitude, type } = this.form.value;
    if (latitude == null || longitude == null || !type) { this.nearbyDuplicates = []; return; }

    this.checkingDuplicates = true;
    try {
      this.nearbyDuplicates = await this.occurrenceSupportService.findNearby(latitude, longitude, type);
    } catch {
      this.nearbyDuplicates = [];
    } finally {
      this.checkingDuplicates = false;
    }
  }

  goToLogin() { this.router.navigate(['/account/sign-in']); }

  isSupported(id?: number): boolean {
    return id != null && this.supportedIds.has(id);
  }

  async toggleSupportDuplicate(o: Occurrence) {
    if (this.auth.isVisitor()) { this.showLoginPrompt = true; return; }
    if (o.id == null || this.supportingId != null) return;
    const apoiando = this.isSupported(o.id);
    this.supportingId = o.id;
    try {
      const info = await this.occurrenceSupportService.toggle(o.id, apoiando);
      if (info.supportedByMe) this.supportedIds.add(o.id);
      else                    this.supportedIds.delete(o.id);
      this.toastr[apoiando ? 'info' : 'success'](
        apoiando ? 'Apoio removido.' : 'Apoio registrado. Obrigado por colaborar!');
    } catch {
      this.toastr.error(apoiando
        ? 'Não foi possível remover o apoio.'
        : 'Não foi possível registrar o apoio.');
    } finally {
      this.supportingId = null;
    }
  }

  private async loadMySupports() {
    if (this.auth.isVisitor()) return;
    try { this.supportedIds = new Set(await this.occurrenceSupportService.mySupports()); }
    catch { this.supportedIds = new Set(); }
  }

  async onPhotoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      this.toastr.error('Selecione um arquivo de imagem.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.toastr.error('A imagem deve ter no máximo 10 MB.');
      return;
    }
    if (!this.form.value.type) {
      this.toastr.warning('Selecione a categoria antes de enviar a foto.');
      input.value = '';
      return;
    }

    if (this.photos.length >= this.MAX_PHOTOS) {
      this.toastr.warning(`Máximo de ${this.MAX_PHOTOS} fotos por ocorrência.`);
      input.value = '';
      return;
    }

    this.photoState = 'uploading';
    this.photoMessage = '';
    try {
      const { uploadId } = await this.occurrenceCreateService.uploadMedia(file, this.form.value.type);
      await this.pollUpload(uploadId);
    } catch (err: any) {
      this.photoState = 'error';
      this.photoMessage = err?.error?.error || 'Falha ao enviar a foto. Tente novamente.';
    } finally {
      input.value = '';
    }
  }

  private async pollUpload(uploadId: string) {
    for (let i = 0; i < 30; i++) {
      try {
        const status = await this.occurrenceCreateService.getUploadStatus(uploadId);
        if (status?.state === 'DONE') {
          this.photos.push({
            url: status.url,
            cloudinaryPublicId: status.publicId,
            imageBlurred: !!status.blurred
          });
          this.photoState = 'idle';
          return;
        }
      } catch (err: any) {
        const body = err?.error;
        if (body?.state === 'REJECTED') {
          this.photoState = 'rejected';
          this.photoMessage = body.message || 'Foto rejeitada (muito borrada para esta categoria).';
        } else {
          this.photoState = 'error';
          this.photoMessage = 'Erro ao processar a foto.';
        }
        return;
      }
      await this.delay(1000);
    }
    this.photoState = 'error';
    this.photoMessage = 'Tempo esgotado ao processar a foto.';
  }

  removePhoto(index: number) {
    this.photos.splice(index, 1);
  }

  dismissPhotoError() {
    this.photoState = 'idle';
    this.photoMessage = '';
  }

  private delay(ms: number) { return new Promise(r => setTimeout(r, ms)); }

  async create() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (this.photoState === 'uploading') {
      this.toastr.info('Aguarde o processamento da foto.');
      return;
    }
    this.loading = true;

    const v = this.form.value;
    const userEmail = localStorage.getItem('email');

    const payload = {
      title:            v.title,
      description:      v.description,
      type:             v.type,
      city:             v.city,
      state:            this.locality.normalizeUf(v.state) ?? undefined,
      neighborhood:     v.neighborhood,
      street:           v.street,
      number:           v.number       || null,
      addressReference: v.addressReference || null,
      latitude:         v.latitude,
      longitude:        v.longitude,
      urlMedia:           this.photos[0]?.url || null,
      cloudinaryPublicId: this.photos[0]?.cloudinaryPublicId || null,
      media:              this.photos,
      email:        v.anonymous ? null : userEmail
    };

    try {
      const res: any = await this.occurrenceCreateService.create(payload);
      this.loading = false;
      if (res?.trackingCode) {
        this.trackingCode = res.trackingCode;
      } else {
        this.toastr.success(`Ocorrência registrada! Protocolo: ${res?.protocolNumber || ''}`);
        this.router.navigate(['/occurrence/list']);
      }
    } catch (err: any) {
      this.loading = false;
      this.toastr.error(err?.error?.error || 'Erro ao registrar a ocorrência. Tente novamente.');
    }
  }

  dismissTrackingCode() {
    this.trackingCode = null;
    this.router.navigate(['/occurrence/list']);
  }
}

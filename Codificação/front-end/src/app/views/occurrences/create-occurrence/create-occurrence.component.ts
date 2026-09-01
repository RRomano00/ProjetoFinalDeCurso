import { Component, OnInit, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';
import { OccurrenceCreateService } from '../../../services/occurrence-create.service';
import { OccurrenceSupportService } from '../../../services/occurrence-support.service';
import { GeocodingService } from '../../../services/local/geocoding.service';
import { Occurrence } from '../../../domain/model/occurrence';
import { OCCURRENCE_TYPES } from '../../../domain/occurrence-labels';
import { ToastrService } from 'ngx-toastr';
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
  form!: FormGroup;
  loading = false;
  trackingCode: string | null = null;

  // Estado do mapa / geocodificação (RF09)
  geocodeStatus: 'idle' | 'loading' | 'success' = 'idle';
  private map!: L.Map;
  private marker?: L.Marker;

  // RF07/RF20: fotos anexadas (até MAX_PHOTOS; upload uma a uma)
  readonly MAX_PHOTOS = 3;
  photos: { url: string; cloudinaryPublicId: string; imageBlurred: boolean }[] = [];
  photoState: 'idle' | 'uploading' | 'rejected' | 'error' = 'idle';
  photoMessage = '';

  // RF16: duplicatas próximas (50 m) do mesmo tipo
  nearbyDuplicates: Occurrence[] = [];
  checkingDuplicates = false;
  supportedIds = new Set<number>();
  supportingId: number | null = null;
  /** RF08/RF11: visitante tentou apoiar → pede login. */
  showLoginPrompt = false;

  /** Visitante sem conta (RF08): ocorrência é obrigatoriamente anônima. */
  get isVisitor(): boolean {
    return !localStorage.getItem('token');
  }

  // Categorias compartilhadas (domain/occurrence-labels)
  occurrenceTypes = OCCURRENCE_TYPES;

  constructor(
    private occurrenceCreateService: OccurrenceCreateService,
    private occurrenceSupportService: OccurrenceSupportService,
    private geocodingService: GeocodingService,
    private fb: FormBuilder,
    private router: Router,
    private toastr: ToastrService,
    private ngZone: NgZone
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
      city:             ['Santa Rita do Sapucaí', Validators.required],
      latitude:         [null],
      longitude:        [null],
      anonymous:        [false]
    });

    // RF08: visitante só registra como anônimo
    if (this.isVisitor) this.form.patchValue({ anonymous: true });

    // RF16: mudou a categoria com local já marcado → verifica duplicatas de novo
    this.form.get('type')!.valueChanges.subscribe(() => this.checkDuplicates());
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.initMap();
      this.requestCurrentLocation();
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

    // Clique no mapa define a localização e preenche o endereço (RF09)
    this.map.on('click', (e: L.LeafletMouseEvent) => {
      this.ngZone.run(() => this.setLocation(e.latlng.lat, e.latlng.lng, true));
    });
  }

  /** RF09: solicita autorização para capturar a localização atual via API de Geolocalização. */
  private requestCurrentLocation() {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => this.ngZone.run(() => {
        const { latitude, longitude } = pos.coords;
        this.map.setView([latitude, longitude], 17);
        this.setLocation(latitude, longitude, true);
      }),
      () => {
        // Negado/indisponível: o usuário clica no mapa ou preenche o endereço manualmente
        this.toastr.info('Não foi possível obter sua localização. Clique no mapa ou preencha o endereço.');
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  }

  /** Posiciona o marcador, grava lat/lng e (opcionalmente) preenche o endereço via reverse geocoding. */
  private async setLocation(lat: number, lng: number, fillAddress: boolean) {
    this.form.patchValue({ latitude: lat, longitude: lng });
    this.checkDuplicates();   // RF16: novo local → verifica duplicatas próximas

    if (this.marker) this.marker.setLatLng([lat, lng]);
    else this.marker = L.marker([lat, lng]).addTo(this.map);

    if (!fillAddress) return;

    this.geocodeStatus = 'loading';
    const addr = await this.geocodingService.reverseGeocode(lat, lng);
    this.ngZone.run(() => {
      if (addr) {
        // Preenche logradouro, bairro e município — NÃO o número (item 3, sempre manual)
        this.form.patchValue({
          street:       addr.street       || this.form.value.street,
          neighborhood: addr.neighborhood || this.form.value.neighborhood,
          city:         addr.city         || this.form.value.city
        });
        this.geocodeStatus = 'success';
      } else {
        this.geocodeStatus = 'idle';
      }
    });
  }

  // ── RF16: duplicatas próximas + apoiar ────────────────────────────────────

  /** Com local marcado E categoria escolhida, busca ocorrências abertas do mesmo tipo num raio de 50 m. */
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

  /** Apoia uma ocorrência existente em vez de criar outra igual. */
  async supportDuplicate(o: Occurrence) {
    if (this.isVisitor) { this.showLoginPrompt = true; return; }
    if (!o.id || this.supportedIds.has(o.id)) return;
    this.supportingId = o.id;
    try {
      await this.occurrenceSupportService.support(o.id);
      this.supportedIds.add(o.id);
      this.toastr.success('Apoio registrado. Obrigado por colaborar!');
    } catch {
      this.toastr.error('Não foi possível registrar o apoio.');
    } finally {
      this.supportingId = null;
    }
  }

  // ── Upload de foto (RF07/RF08/RF20) ──────────────────────────────────────

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
    } catch {
      this.photoState = 'error';
      this.photoMessage = 'Falha ao enviar a foto. Tente novamente.';
    } finally {
      input.value = '';
    }
  }

  /** Consulta o processamento até concluir e adiciona a foto à lista (RF07). */
  private async pollUpload(uploadId: string) {
    for (let i = 0; i < 30; i++) {       // até ~30s
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
        // PROCESSING → aguarda e tenta de novo
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
    } catch {
      this.loading = false;
      this.toastr.error('Erro ao registrar a ocorrência. Tente novamente.');
    }
  }

  dismissTrackingCode() {
    this.trackingCode = null;
    this.router.navigate(['/occurrence/list']);
  }
}

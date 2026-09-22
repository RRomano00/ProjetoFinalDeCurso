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
  /** UF e municípios do endereço da ocorrência. */
  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  /**
   * Cobertura do município informado: null = ainda não se sabe, true = há equipe,
   * false = município sem adesão. Só muda o aviso; nunca impede o registro.
   */
  cityServed: boolean | null = null;

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

  // Categorias compartilhadas (domain/occurrence-labels)
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
      // O município é o do ENDEREÇO da ocorrência, não o do cadastro de quem
      // registra: quem mora em Santa Rita pode relatar um problema em Itajubá.
      // A UF vem antes porque é ela que define a lista de municípios.
      state:            ['MG', Validators.required],
      city:             ['Santa Rita do Sapucaí', Validators.required],
      latitude:         [null],
      longitude:        [null],
      anonymous:        [false]
    });

    // RF08: visitante só registra como anônimo
    if (this.auth.isVisitor()) this.form.patchValue({ anonymous: true });

    // RF16: mudou a categoria com local já marcado → verifica duplicatas de novo
    this.form.get('type')!.valueChanges.subscribe(() => this.checkDuplicates());

    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);

    // Município completo (por digitação ou pelo mapa) → confere a cobertura.
    this.form.get('city')!.valueChanges.subscribe(() => this.checkCoverage());
    this.form.get('state')!.valueChanges.subscribe(() => this.checkCoverage());
    this.checkCoverage();

    // RF09: o endereço digitado leva o mapa até lá. A pausa é porque cada tecla
    // dispara valueChanges e o Nominatim aceita uma consulta por segundo.
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

  /**
   * RF09: solicita autorização para capturar a localização atual via API de
   * Geolocalização. Passa pelo serviço de município para usar as duas
   * tentativas e o mesmo aviso de GPS desligado das demais telas; sem posição,
   * a pessoa clica no mapa ou preenche o endereço à mão.
   */
  private async requestCurrentLocation() {
    // Primeiro o município de quem registra, que não depende do aparelho: o
    // mapa já fica útil enquanto o navegador pergunta pela localização.
    const center = await this.localityPreference.mapCenter();
    if (center) this.ngZone.run(() => this.map.setView([center.lat, center.lng], center.zoom));

    const position = await this.localityPreference.position();
    if (!position) return;
    this.ngZone.run(() => {
      const { latitude, longitude } = position.coords;
      this.map.setView([latitude, longitude], 17);
      this.setLocation(latitude, longitude, true);
    });
  }

  /**
   * Leva o mapa ao endereço digitado — a vista, não o marcador: o geocodificador
   * acerta a rua, não o número, e o ponto exato continua sendo o do clique (ou
   * o do GPS), que é o que vai para o cadastro.
   */
  private async centerOnTypedAddress() {
    if (!this.map) return;
    const { street, neighborhood, city, state } = this.form.value;
    if (!city) return;   // sem município não há o que procurar

    const local = [city, state].filter(Boolean).join(', ');
    const busca = [street, neighborhood, local].filter(Boolean).join(' | ');
    // Nem repete a mesma consulta, nem desfaz o que o próprio mapa preencheu:
    // depois de um clique, voltar para o centro da rua afastaria do ponto certo.
    if (busca === this.lastCentered || busca === this.addressFromMap) return;
    this.lastCentered = busca;

    const coords = await this.geocodingService.geocode(street || '', neighborhood || '', local);
    if (coords) this.ngZone.run(() => this.map.setView([coords.lat, coords.lng], street ? 16 : 13));
  }

  /** Última consulta enviada, e o endereço que veio do próprio mapa. */
  private lastCentered  = '';
  private addressFromMap = '';

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
        // A UF entra antes do município: é ela que carrega a lista de
        // sugestões, e o ponto marcado no mapa pode estar em outro estado.
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
        this.geocodeStatus = 'success';
      } else {
        this.geocodeStatus = 'idle';
      }
    });
  }

  /**
   * Avisa quando o município ainda não tem equipe no sistema. O registro segue
   * permitido: a ocorrência fica guardada e aparece para o administrador.
   */
  private async checkCoverage() {
    const { city, state } = this.form.value;
    this.cityServed = await this.coverage.isServed(city, state);
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

  isSupported(id?: number): boolean {
    return id != null && this.supportedIds.has(id);
  }

  /**
   * Apoia ou desfaz o apoio a uma duplicata, em vez de criar outra igual.
   * O estado sai da resposta do servidor: antes o conjunto local começava vazio,
   * então uma ocorrência que o usuário JÁ apoiava aparecia como "Apoiar" e o
   * clique respondia "Apoio registrado" sem o banco mudar nada.
   */
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

  /** Carrega os apoios que o usuário já tem, para o botão nascer com o estado certo. */
  private async loadMySupports() {
    if (this.auth.isVisitor()) return;
    try { this.supportedIds = new Set(await this.occurrenceSupportService.mySupports()); }
    catch { this.supportedIds = new Set(); }
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
    } catch (err: any) {
      this.photoState = 'error';
      // 400 (não é imagem) e 429 (limite diário por IP) vêm com a mensagem do back-end.
      this.photoMessage = err?.error?.error || 'Falha ao enviar a foto. Tente novamente.';
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
      // 429 traz o limite diário atingido e o horário em que ele é renovado.
      this.toastr.error(err?.error?.error || 'Erro ao registrar a ocorrência. Tente novamente.');
    }
  }

  dismissTrackingCode() {
    this.trackingCode = null;
    this.router.navigate(['/occurrence/list']);
  }
}

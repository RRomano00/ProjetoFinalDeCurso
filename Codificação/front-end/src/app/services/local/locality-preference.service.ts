import { Injectable } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { GeocodingService } from './geocoding.service';
import { UserReadService } from '../user/user-read.service';

export interface Municipality {
  city:  string;
  state: string;
}

@Injectable({ providedIn: 'root' })
export class LocalityPreferenceService {
  private static readonly CHOICE = 'locality.choice';

  private cadastro: Municipality | null | undefined;

  private center: { lat: number; lng: number; zoom: number } | null | undefined;

  constructor(private geocoding: GeocodingService,
              private userRead: UserReadService,
              private toastr: ToastrService) {}

  async ensure(): Promise<Municipality | null> {
    const atual = this.choice;
    if (atual) return atual;
    if (await this.deniedBefore()) return null;
    return this.detect();
  }

  async ofCurrentUser(): Promise<Municipality | null> {
    if (this.cadastro !== undefined) return this.cadastro;
    this.cadastro = null;
    const id = localStorage.getItem('id');
    if (id) {
      try {
        const eu = await this.userRead.findById(id);
        if (eu?.city) this.cadastro = { city: eu.city, state: (eu.state || '').trim().toUpperCase() };
      } catch { }
    }
    return this.cadastro;
  }

  get choice(): Municipality | null {
    try {
      const saved = JSON.parse(localStorage.getItem(LocalityPreferenceService.CHOICE) || 'null');
      return saved?.city ? { city: saved.city, state: saved.state || '' } : null;
    } catch {
      return null;
    }
  }

  set choice(municipality: Municipality | null) {
    this.center = undefined;
    if (municipality?.city) {
      localStorage.setItem(LocalityPreferenceService.CHOICE, JSON.stringify({
        city:  municipality.city.trim(),
        state: (municipality.state || '').trim().toUpperCase(),
      }));
    } else {
      localStorage.removeItem(LocalityPreferenceService.CHOICE);
    }
  }

  async deniedBefore(): Promise<boolean> {
    try {
      const status = await navigator.permissions?.query({ name: 'geolocation' as PermissionName });
      return status?.state === 'denied';
    } catch {
      return false;
    }
  }

  private tentativa(alta: boolean, timeout = 10000): Promise<GeolocationPosition | GeolocationPositionError> {
    return new Promise(resolve => {
      if (!navigator.geolocation) {
        resolve({ code: 2, message: 'navegador sem API de geolocalização' } as GeolocationPositionError);
        return;
      }
      navigator.geolocation.getCurrentPosition(resolve, resolve,
        { timeout, maximumAge: 300000, enableHighAccuracy: alta });
    });
  }

  /** `quick`: uma tentativa de até 5 s, para quem está esperando num popup (normal: até 2 × 10 s). */
  async position(quick = false): Promise<GeolocationPosition | null> {
    let resultado = await this.tentativa(false, quick ? 5000 : 10000);
    if ('coords' in resultado) return resultado;

    if (!quick) {
      resultado = await this.tentativa(true);
      if ('coords' in resultado) return resultado;
    }

    console.warn(`[localização] ${resultado.code} — ${resultado.message}`);
    const negada = resultado.code === 1;
    this.toastr.warning(
      negada
        ? 'Libere o acesso à localização nas permissões do navegador para este site.'
        : 'Ligue o GPS/localização do aparelho e tente de novo',
      negada ? 'Permissão de localização negada' : 'Localização indisponível');
    return null;
  }

  async detect(quick = false): Promise<Municipality | null> {
    const position = await this.position(quick);
    if (!position) return null;
    return this.municipalityAt(position.coords.latitude, position.coords.longitude);
  }

  /** Município de uma posição (ex.: do GPS), que passa a ser a escolha lembrada. */
  async municipalityAt(lat: number, lng: number): Promise<Municipality | null> {
    const address = await this.geocoding.reverseGeocode(lat, lng);
    if (!address?.city) return null;
    const municipality = { city: address.city, state: address.state || '' };
    this.choice = municipality;
    return municipality;
  }

  /** `visitor`: município escolhido ao entrar como anônimo; o mapa abre sempre nele. */
  async startingMunicipality(visitor?: Municipality | null): Promise<Municipality | null> {
    if (visitor) {
      this.choice = visitor;
      return visitor;
    }
    const cadastro = await this.ofCurrentUser();
    if (cadastro) { this.choice = cadastro; return cadastro; }
    return this.choice;
  }

  async mapCenter(): Promise<{ lat: number; lng: number; zoom: number } | null> {
    if (this.center !== undefined) return this.center;
    const municipio = this.choice ?? await this.ofCurrentUser();
    return this.center = municipio ? await this.centerOf(municipio) : null;
  }

  private async centerOf(municipality: Municipality) {
    const coords = await this.geocoding.geocode('', municipality.city, municipality.state);
    return coords ? { ...coords, zoom: 13 } : null;
  }

  static fold(value?: string): string {
    return (value || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').trim().toLowerCase();
  }

  static label(municipality: Municipality): string {
    return municipality.state ? `${municipality.city}/${municipality.state}` : municipality.city;
  }

  static matches(choice: Municipality | null, city?: string, state?: string): boolean {
    if (!choice) return true;
    if (LocalityPreferenceService.fold(choice.city) !== LocalityPreferenceService.fold(city)) return false;
    const chosen = (choice.state || '').toUpperCase();
    const other  = (state || '').trim().toUpperCase();
    return !chosen || !other || chosen === other;
  }

  static options(list: { city?: string; state?: string }[],
                 extras: (Municipality | null | undefined)[] = []): Municipality[] {
    const found = new Map<string, Municipality>();
    for (const item of [...list, ...extras.filter(Boolean) as Municipality[]]) {
      const key = LocalityPreferenceService.fold(item.city);
      if (!key) continue;
      const known = found.get(key);
      if (!known) {
        found.set(key, { city: item.city!.trim(), state: (item.state || '').trim().toUpperCase() });
      } else if (!known.state && item.state) {
        known.state = item.state.trim().toUpperCase();
      }
    }
    return [...found.values()].sort((a, b) => a.city.localeCompare(b.city, 'pt-BR'));
  }
}

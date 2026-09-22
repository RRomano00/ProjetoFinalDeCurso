import { Injectable } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { GeocodingService } from './geocoding.service';
import { UserReadService } from '../user/user-read.service';

export interface Municipality {
  city:  string;
  /** UF; vazia nas ocorrências antigas, cadastradas antes do campo existir. */
  state: string;
}

/**
 * Qual município a pessoa está olhando.
 *
 * É preferência de visualização, não cadastro: quem mora em Santa Rita pode
 * acompanhar Itajubá. Por isso fica no navegador, e não no perfil.
 * Sem escolha, a tela mostra as ocorrências de todos os municípios.
 */
@Injectable({ providedIn: 'root' })
export class LocalityPreferenceService {
  private static readonly CHOICE = 'locality.choice';

  /** Município do cadastro de quem está logado; lido uma vez por sessão. */
  private cadastro: Municipality | null | undefined;

  /** Centro do mapa já resolvido; undefined = ainda não resolvido nesta sessão. */
  private center: { lat: number; lng: number; zoom: number } | null | undefined;

  constructor(private geocoding: GeocodingService,
              private userRead: UserReadService,
              private toastr: ToastrService) {}

  /**
   * Garante uma escolha de município: devolve a que já existe ou, se ainda não
   * houver e a localização não tiver sido negada, pede ao navegador.
   */
  async ensure(): Promise<Municipality | null> {
    const atual = this.choice;
    if (atual) return atual;
    if (await this.deniedBefore()) return null;
    return this.detect();
  }

  /**
   * Município do cadastro de quem está logado. Entra nas opções do filtro para
   * que a pessoa encontre o seu município mesmo quando ele ainda não tem
   * nenhuma ocorrência registrada.
   */
  async ofCurrentUser(): Promise<Municipality | null> {
    if (this.cadastro !== undefined) return this.cadastro;
    this.cadastro = null;
    const id = localStorage.getItem('id');
    if (id) {
      try {
        const eu = await this.userRead.findById(id);
        if (eu?.city) this.cadastro = { city: eu.city, state: (eu.state || '').trim().toUpperCase() };
      } catch { /* sem o perfil, o filtro fica só com o que veio das ocorrências */ }
    }
    return this.cadastro;
  }

  /** Município escolhido; null = todos. */
  get choice(): Municipality | null {
    try {
      const saved = JSON.parse(localStorage.getItem(LocalityPreferenceService.CHOICE) || 'null');
      return saved?.city ? { city: saved.city, state: saved.state || '' } : null;
    } catch {
      return null;
    }
  }

  set choice(municipality: Municipality | null) {
    this.center = undefined;   // trocou de município: o centro do mapa é outro
    if (municipality?.city) {
      localStorage.setItem(LocalityPreferenceService.CHOICE, JSON.stringify({
        city:  municipality.city.trim(),
        state: (municipality.state || '').trim().toUpperCase(),
      }));
    } else {
      localStorage.removeItem(LocalityPreferenceService.CHOICE);
    }
  }

  /**
   * A localização já foi recusada neste navegador? Quando foi, o pedido nem é
   * feito: o navegador não mostraria nada e só sobraria erro no console.
   */
  async deniedBefore(): Promise<boolean> {
    try {
      const status = await navigator.permissions?.query({ name: 'geolocation' as PermissionName });
      return status?.state === 'denied';
    } catch {
      return false;   // navegador sem Permissions API: tenta e deixa ele decidir
    }
  }

  /** Uma tentativa de leitura: devolve a posição ou o erro que a impediu. */
  private tentativa(alta: boolean): Promise<GeolocationPosition | GeolocationPositionError> {
    return new Promise(resolve => {
      if (!navigator.geolocation) {
        resolve({ code: 2, message: 'navegador sem API de geolocalização' } as GeolocationPositionError);
        return;
      }
      navigator.geolocation.getCurrentPosition(resolve, resolve,
        { timeout: 10000, maximumAge: 300000, enableHighAccuracy: alta });
    });
  }

  /**
   * Posição atual do aparelho, com aviso na tela quando não vem.
   *
   * A primeira tentativa usa o provedor rápido (rede). Quando ele falha — o
   * caso do computador com extensão de bloqueio —, a segunda vai ao receptor
   * do aparelho, que é o que costuma responder no celular. Só depois das duas
   * é que o aviso aparece, e ele diz o que fazer: recusa se resolve no
   * navegador, GPS desligado se resolve no aparelho.
   */
  async position(): Promise<GeolocationPosition | null> {
    let resultado = await this.tentativa(false);
    if ('coords' in resultado) return resultado;

    resultado = await this.tentativa(true);
    if ('coords' in resultado) return resultado;

    console.warn(`[localização] ${resultado.code} — ${resultado.message}`);
    const negada = resultado.code === 1;   // PERMISSION_DENIED
    this.toastr.warning(
      negada
        ? 'Libere o acesso à localização nas permissões do navegador para este site.'
        : 'Ligue o GPS/localização do aparelho e tente de novo',
      negada ? 'Permissão de localização negada' : 'Localização indisponível');
    return null;
  }

  /**
   * Pede a localização ao navegador e converte em município, que passa a ser a
   * escolha. Devolve null quando a pessoa não libera — aí a tela fica global.
   */
  async detect(): Promise<Municipality | null> {
    const position = await this.position();
    if (!position) return null;

    const address = await this.geocoding.reverseGeocode(
      position.coords.latitude, position.coords.longitude);
    if (!address?.city) return null;

    const municipality = { city: address.city, state: address.state || '' };
    this.choice = municipality;
    return municipality;
  }

  /**
   * Onde o mapa abre: o município escolhido no filtro, ou, na falta dele, o do
   * cadastro. Null quando não há nenhum dos dois — aí o mapa fica onde estava.
   *
   * O GPS NÃO entra aqui de propósito. Ele é correção, não abertura: perguntar
   * ao aparelho leva segundos e deixava a tela parada esperando para só então
   * cair no município. Quem quer o ponto do aparelho chama `position()` depois
   * de já ter desenhado o mapa — é o que fazem a nova ocorrência (RF09) e o
   * `ensure()` do Início.
   *
   * Resolvido uma vez por sessão: a geocodificação é ida à rede e o centro não
   * muda entre telas. Escolher outro município descarta o que estava guardado.
   */
  async mapCenter(): Promise<{ lat: number; lng: number; zoom: number } | null> {
    if (this.center !== undefined) return this.center;
    const municipio = this.choice ?? await this.ofCurrentUser();
    return this.center = municipio ? await this.centerOf(municipio) : null;
  }

  /** Município -> coordenadas, no zoom que mostra a cidade inteira. */
  private async centerOf(municipality: Municipality) {
    const coords = await this.geocoding.geocode('', municipality.city, municipality.state);
    return coords ? { ...coords, zoom: 13 } : null;
  }

  /** "itajuba" acha "Itajubá": município se compara sem acento nem caixa. */
  static fold(value?: string): string {
    return (value || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').trim().toLowerCase();
  }

  /** Rótulo do município nas telas: "Itajubá/MG" (ou só o nome, sem UF). */
  static label(municipality: Municipality): string {
    return municipality.state ? `${municipality.city}/${municipality.state}` : municipality.city;
  }

  /**
   * A ocorrência é do município escolhido? A UF só desempata quando os dois
   * lados a informam: ocorrência antiga não tem UF e não pode sumir da lista.
   */
  static matches(choice: Municipality | null, city?: string, state?: string): boolean {
    if (!choice) return true;
    if (LocalityPreferenceService.fold(choice.city) !== LocalityPreferenceService.fold(city)) return false;
    const chosen = (choice.state || '').toUpperCase();
    const other  = (state || '').trim().toUpperCase();
    return !chosen || !other || chosen === other;
  }

  /**
   * Opções do filtro: os municípios presentes na lista carregada mais os
   * avulsos (o escolhido e o do cadastro), que podem ainda não ter ocorrência.
   */
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
        known.state = item.state.trim().toUpperCase();   // registro antigo herda a UF do novo
      }
    }
    return [...found.values()].sort((a, b) => a.city.localeCompare(b.city, 'pt-BR'));
  }
}

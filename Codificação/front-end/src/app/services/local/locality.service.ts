import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

export interface FederativeUnit {
  uf: string;
  name: string;
}

export interface CityOptions {
  list: string[];
  ready: boolean;
}

@Injectable({ providedIn: 'root' })
export class LocalityService {

  private static readonly IBGE = 'https://servicodados.ibge.gov.br/api/v1/localidades';

  readonly units: FederativeUnit[] = [
    { uf: 'AC', name: 'Acre' },              { uf: 'AL', name: 'Alagoas' },
    { uf: 'AP', name: 'Amapá' },             { uf: 'AM', name: 'Amazonas' },
    { uf: 'BA', name: 'Bahia' },             { uf: 'CE', name: 'Ceará' },
    { uf: 'DF', name: 'Distrito Federal' },  { uf: 'ES', name: 'Espírito Santo' },
    { uf: 'GO', name: 'Goiás' },             { uf: 'MA', name: 'Maranhão' },
    { uf: 'MT', name: 'Mato Grosso' },       { uf: 'MS', name: 'Mato Grosso do Sul' },
    { uf: 'MG', name: 'Minas Gerais' },      { uf: 'PA', name: 'Pará' },
    { uf: 'PB', name: 'Paraíba' },           { uf: 'PR', name: 'Paraná' },
    { uf: 'PE', name: 'Pernambuco' },        { uf: 'PI', name: 'Piauí' },
    { uf: 'RJ', name: 'Rio de Janeiro' },    { uf: 'RN', name: 'Rio Grande do Norte' },
    { uf: 'RS', name: 'Rio Grande do Sul' }, { uf: 'RO', name: 'Rondônia' },
    { uf: 'RR', name: 'Roraima' },           { uf: 'SC', name: 'Santa Catarina' },
    { uf: 'SP', name: 'São Paulo' },         { uf: 'SE', name: 'Sergipe' },
    { uf: 'TO', name: 'Tocantins' }
  ];

  private readonly cache = new Map<string, string[]>();

  constructor(private http: HttpClient) {}

  normalizeUf(value: string | null | undefined): string | null {
    const v = (value || '').trim();
    if (!v) return null;
    const upper = v.toUpperCase();
    if (this.units.some(u => u.uf === upper)) return upper;
    const byName = this.units.find(
      u => u.name.localeCompare(v, 'pt-BR', { sensitivity: 'base' }) === 0);
    return byName ? byName.uf : null;
  }

  bindCityToUf(form: FormGroup, ufControl = 'state', cityControl = 'city'): CityOptions {
    const uf   = form.get(ufControl);
    const city = form.get(cityControl);
    const options: CityOptions = { list: [], ready: false };
    if (!uf || !city) return options;

    const apply = async (value: string | null, userChanged: boolean) => {
      const sigla = this.normalizeUf(value);
      options.ready = !!sigla;
      options.list  = sigla ? await this.cities(sigla) : [];
      if (userChanged && sigla && city.value && options.list.length
          && !options.list.some(c => c.localeCompare(city.value, 'pt-BR', { sensitivity: 'base' }) === 0)) {
        city.setValue('', { emitEvent: false });
      }
    };

    uf.valueChanges.subscribe(v => apply(v, true));
    apply(uf.value, false);
    return options;
  }

  async cities(uf: string | null | undefined): Promise<string[]> {
    const sigla = this.normalizeUf(uf);
    if (!sigla) return [];

    const cached = this.cache.get(sigla);
    if (cached) return cached;

    try {
      const data = await firstValueFrom(
        this.http.get<{ nome: string }[]>(
          `${LocalityService.IBGE}/estados/${sigla}/municipios?orderBy=nome`)
      );
      const names = (data || []).map(m => m.nome).filter(Boolean);
      this.cache.set(sigla, names);
      return names;
    } catch {
      return [];
    }
  }
}

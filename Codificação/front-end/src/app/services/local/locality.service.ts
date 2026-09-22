import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

/** Unidade da federação: sigla usada no cadastro e nome para a sugestão. */
export interface FederativeUnit {
  uf: string;
  name: string;
}

/**
 * Municípios sugeridos para a UF corrente. O objeto é estável e tem a lista
 * trocada dentro dele, então o template pode iterar `options.list` sem
 * reatribuição no componente.
 */
export interface CityOptions {
  list: string[];
  /** Falso enquanto não houver UF válida: é o que trava o campo de município. */
  ready: boolean;
}

/**
 * UF e municípios para os campos de cadastro.
 *
 * As 27 unidades da federação são fixas e ficam aqui — não vale uma chamada de
 * rede para uma lista que não muda. Os municípios vêm da API pública de
 * localidades do IBGE, uma vez por UF, e ficam em memória durante a sessão.
 *
 * Nada disso é obrigatório para o cadastro funcionar: os campos são de texto
 * livre e a lista apenas autocompleta. Se o IBGE estiver fora do ar, a pessoa
 * digita o município e segue.
 */
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

  /** Municípios já buscados, por UF. */
  private readonly cache = new Map<string, string[]>();

  constructor(private http: HttpClient) {}

  /** Aceita "MG", "mg" ou "Minas Gerais" e devolve a sigla, ou null. */
  normalizeUf(value: string | null | undefined): string | null {
    const v = (value || '').trim();
    if (!v) return null;
    const upper = v.toUpperCase();
    if (this.units.some(u => u.uf === upper)) return upper;
    const byName = this.units.find(
      u => u.name.localeCompare(v, 'pt-BR', { sensitivity: 'base' }) === 0);
    return byName ? byName.uf : null;
  }

  /**
   * Liga o campo de município ao de UF: as sugestões só existem depois de uma
   * UF válida, e até lá o município fica somente-leitura.
   *
   * Trava com `readonly`, não com `disable()`: um controle desabilitado sai do
   * `form.value`, e um perfil salvo com a UF em branco apagaria o município que
   * já estava gravado.
   */
  bindCityToUf(form: FormGroup, ufControl = 'state', cityControl = 'city'): CityOptions {
    const uf   = form.get(ufControl);
    const city = form.get(cityControl);
    const options: CityOptions = { list: [], ready: false };
    if (!uf || !city) return options;

    const apply = async (value: string | null, userChanged: boolean) => {
      const sigla = this.normalizeUf(value);
      options.ready = !!sigla;
      options.list  = sigla ? await this.cities(sigla) : [];
      // Trocar de UF invalida o município digitado para a UF anterior. Só vale
      // quando quem trocou foi a pessoa: na carga inicial o que está gravado
      // fica como está, ainda que a grafia divirja da lista do IBGE.
      if (userChanged && sigla && city.value && options.list.length
          && !options.list.some(c => c.localeCompare(city.value, 'pt-BR', { sensitivity: 'base' }) === 0)) {
        city.setValue('', { emitEvent: false });
      }
    };

    uf.valueChanges.subscribe(v => apply(v, true));
    apply(uf.value, false);   // estado inicial: perfil já carregado ou UF padrão do formulário
    return options;
  }

  /**
   * Municípios da UF, em ordem alfabética. Devolve lista vazia quando a UF é
   * inválida ou o serviço do IBGE não responde — o campo continua utilizável.
   */
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

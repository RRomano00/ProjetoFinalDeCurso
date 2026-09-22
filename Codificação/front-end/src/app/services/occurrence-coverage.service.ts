import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Cobertura do município: há equipe cadastrada para atender ali?
 *
 * Serve só para avisar quem registra — o registro é aceito de qualquer forma.
 * A resposta fica em cache na sessão porque a mesma pergunta se repete a cada
 * ajuste do endereço.
 */
@Injectable({ providedIn: 'root' })
export class OccurrenceCoverageService {

  private readonly cache = new Map<string, boolean>();

  constructor(private http: HttpClient) {}

  async isServed(city: string | null | undefined, state: string | null | undefined): Promise<boolean | null> {
    const cidade = (city || '').trim();
    if (!cidade) return null;
    const uf  = (state || '').trim().toUpperCase();
    const key = `${uf}|${cidade.toLowerCase()}`;

    const cached = this.cache.get(key);
    if (cached !== undefined) return cached;

    try {
      const url = `${environment.api_endpoint}/occurrence/coverage`
                + `?city=${encodeURIComponent(cidade)}&state=${encodeURIComponent(uf)}`;
      const res = await firstValueFrom(this.http.get<{ served: boolean }>(url));
      this.cache.set(key, !!res?.served);
      return !!res?.served;
    } catch {
      // Sem resposta do servidor não se afirma nada: melhor não avisar do que
      // avisar errado que o município não é atendido.
      return null;
    }
  }
}

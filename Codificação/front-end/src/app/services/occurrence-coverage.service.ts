import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

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
      return null;
    }
  }
}

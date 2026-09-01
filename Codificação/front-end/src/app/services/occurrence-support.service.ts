import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Occurrence } from '../domain/model/occurrence';

export interface SupportInfo {
  count: number;
  supportedByMe: boolean;
}

/** RF16: detecção de duplicatas próximas (50 m) e apoio a ocorrências. */
@Injectable({ providedIn: 'root' })
export class OccurrenceSupportService {
  private base = `${environment.api_endpoint}/occurrence`;

  constructor(private http: HttpClient) {}

  /** Ocorrências abertas do mesmo tipo num raio de 50 m do ponto. */
  findNearby(lat: number, lng: number, type: string): Promise<Occurrence[]> {
    return firstValueFrom(this.http.get<Occurrence[]>(
      `${this.base}/nearby?lat=${lat}&lon=${lng}&type=${type}`
    ));
  }

  /** Total de apoios + se o usuário logado já apoiou. */
  getSupportInfo(id: number | string): Promise<SupportInfo> {
    return firstValueFrom(this.http.get<SupportInfo>(`${this.base}/${id}/support`));
  }

  /** Registra o apoio do usuário logado. */
  support(id: number | string): Promise<SupportInfo> {
    return firstValueFrom(this.http.post<SupportInfo>(`${this.base}/${id}/support`, {}));
  }
}

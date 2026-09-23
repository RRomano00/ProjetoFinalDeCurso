import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Occurrence } from '../domain/model/occurrence';

export interface SupportInfo {
  count: number;
  supportedByMe: boolean;
}

@Injectable({ providedIn: 'root' })
export class OccurrenceSupportService {
  private base = `${environment.api_endpoint}/occurrence`;

  constructor(private http: HttpClient) {}

  findNearby(lat: number, lng: number, type: string): Promise<Occurrence[]> {
    return firstValueFrom(this.http.get<Occurrence[]>(
      `${this.base}/nearby?lat=${lat}&lon=${lng}&type=${type}`
    ));
  }

  getSupportInfo(id: number | string): Promise<SupportInfo> {
    return firstValueFrom(this.http.get<SupportInfo>(`${this.base}/${id}/support`));
  }

  mySupports(): Promise<number[]> {
    return firstValueFrom(this.http.get<number[]>(`${this.base}/support/mine`));
  }

  support(id: number | string): Promise<SupportInfo> {
    return firstValueFrom(this.http.post<SupportInfo>(`${this.base}/${id}/support`, {}));
  }

  unsupport(id: number | string): Promise<SupportInfo> {
    return firstValueFrom(this.http.delete<SupportInfo>(`${this.base}/${id}/support`));
  }

  toggle(id: number | string, apoiado: boolean): Promise<SupportInfo> {
    return apoiado ? this.unsupport(id) : this.support(id);
  }
}

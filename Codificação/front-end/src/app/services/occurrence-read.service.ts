import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Occurrence, OccurrenceHistory } from '../domain/model/occurrence';

@Injectable({ providedIn: 'root' })
export class OccurrenceReadService {
  constructor(private http: HttpClient) { }

  findAll(): Promise<Occurrence[]> {
    return firstValueFrom(this.http.get<Occurrence[]>(`${environment.api_endpoint}/occurrence`));
  }

  findById(id: string): Promise<Occurrence> {
    return firstValueFrom(this.http.get<Occurrence>(`${environment.api_endpoint}/occurrence/${id}`));
  }

  findByProtocol(protocol: string): Promise<Occurrence> {
    return firstValueFrom(this.http.get<Occurrence>(`${environment.api_endpoint}/occurrence/protocol/${protocol}`));
  }

  /** RN03/RF11: histórico de mudanças de status da ocorrência. */
  getHistory(id: number | string): Promise<OccurrenceHistory[]> {
    return firstValueFrom(this.http.get<OccurrenceHistory[]>(
      `${environment.api_endpoint}/occurrence/${id}/history`
    ));
  }

  /** RF12: ocorrências do mesmo grupo de duplicatas (raiz + encadeadas). */
  getGroup(id: number | string): Promise<Occurrence[]> {
    return firstValueFrom(this.http.get<Occurrence[]>(
      `${environment.api_endpoint}/occurrence/${id}/group`
    ));
  }

  findAnonymous(trackingCode: string): Promise<Occurrence> {
    return firstValueFrom(this.http.get<Occurrence>(
      `${environment.api_endpoint}/occurrence/anonymous-status?trackingCode=${trackingCode}`
    ));
  }
}

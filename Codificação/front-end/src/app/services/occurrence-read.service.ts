import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Occurrence, OccurrenceHistory } from '../domain/model/occurrence';

@Injectable({ providedIn: 'root' })
export class OccurrenceReadService {
  constructor(private http: HttpClient) { }

  findMine(): Promise<Occurrence[]> {
    return firstValueFrom(
      this.http.get<Occurrence[]>(`${environment.api_endpoint}/occurrence/mine`)
    );
  }

  findAll(): Promise<Occurrence[]> {
    return firstValueFrom(this.http.get<Occurrence[]>(`${environment.api_endpoint}/occurrence`));
  }

  findById(id: string): Promise<Occurrence> {
    return firstValueFrom(this.http.get<Occurrence>(`${environment.api_endpoint}/occurrence/${id}`));
  }

  findByProtocol(protocol: string): Promise<Occurrence> {
    return firstValueFrom(this.http.get<Occurrence>(`${environment.api_endpoint}/occurrence/protocol/${protocol}`));
  }

  getHistory(id: number | string): Promise<OccurrenceHistory[]> {
    return firstValueFrom(this.http.get<OccurrenceHistory[]>(
      `${environment.api_endpoint}/occurrence/${id}/history`
    ));
  }

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

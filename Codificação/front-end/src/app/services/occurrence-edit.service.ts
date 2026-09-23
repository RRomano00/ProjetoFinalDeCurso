import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OccurrenceEditService {
  constructor(private http: HttpClient) {}

  updateToInProgress(id: string, observation?: string, collective = false): Promise<any> {
    return firstValueFrom(
      this.http.put<any>(`${environment.api_endpoint}/occurrence/progress/${id}`, { observation, collective })
    );
  }

  updateToConclude(id: string, observation?: string, collective = false): Promise<any> {
    return firstValueFrom(
      this.http.put<any>(`${environment.api_endpoint}/occurrence/conclude/${id}`, { observation, collective })
    );
  }

  updateStatus(id: string, newStatus: string, observation?: string, collective = false): Promise<any> {
    return firstValueFrom(
      this.http.put<any>(`${environment.api_endpoint}/occurrence/${id}/status`, { newStatus, observation, collective })
    );
  }
}

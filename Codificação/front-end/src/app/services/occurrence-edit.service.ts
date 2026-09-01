import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OccurrenceEditService {
  constructor(private http: HttpClient) {}

  /** Inicia o atendimento; mensagem opcional vai ao histórico e ao e-mail do autor (collective = grupo todo, RF12). */
  updateToInProgress(id: string, observation?: string, collective = false): Promise<any> {
    return firstValueFrom(
      this.http.put<any>(`${environment.api_endpoint}/occurrence/progress/${id}`, { observation, collective })
    );
  }

  /** Conclui o atendimento; mensagem opcional vai ao histórico e ao e-mail do autor (collective = grupo todo, RF12). */
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

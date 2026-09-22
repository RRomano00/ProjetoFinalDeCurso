import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * RF22: encaminha a ocorrência ao departamento responsável. O servidor envia o
 * e-mail (sem dados pessoais, com as fotos anexadas) e só então move a
 * ocorrência para Em andamento.
 */
@Injectable({ providedIn: 'root' })
export class OccurrenceForwardService {

  constructor(private http: HttpClient) {}

  forward(occurrenceId: string, departmentId: number): Promise<{ department: string; status: string }> {
    return firstValueFrom(
      this.http.post<{ department: string; status: string }>(
        `${environment.api_endpoint}/occurrence/${occurrenceId}/forward`, { departmentId })
    );
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

/** O que aconteceu com cada destino: um e-mail que saiu não é desfeito porque
 *  o seguinte falhou, então o servidor responde nome a nome. */
export interface ForwardResult {
  departments: string[];
  failed:      string[];
  status:      string;
}

/**
 * RF22: encaminha a ocorrência aos departamentos responsáveis. O servidor envia
 * o e-mail (sem dados pessoais, com as fotos anexadas) e só então move a
 * ocorrência para Em andamento.
 */
@Injectable({ providedIn: 'root' })
export class OccurrenceForwardService {

  constructor(private http: HttpClient) {}

  forward(occurrenceId: string, departmentIds: number[]): Promise<ForwardResult> {
    return firstValueFrom(
      this.http.post<ForwardResult>(
        `${environment.api_endpoint}/occurrence/${occurrenceId}/forward`, { departmentIds })
    );
  }
}

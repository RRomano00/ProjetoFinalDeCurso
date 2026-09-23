import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ForwardResult {
  departments: string[];
  failed:      string[];
  status:      string;
}

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

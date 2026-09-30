import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Occurrence, OccurrenceHistory } from '../domain/model/occurrence';

export interface DepartmentOccurrenceView {
  departmentName: string | null;
  occurrence: Occurrence;
  group: Occurrence[];
  history: OccurrenceHistory[];
  canRequestCompletion: boolean;
  pendingRequestAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class DepartmentAccessService {
  private readonly base = `${environment.api_endpoint}/department-access`;
  constructor(private http: HttpClient) {}

  private headers(token: string) { return new HttpHeaders({ 'X-Department-Access': token }); }

  view(token: string, id?: number): Promise<DepartmentOccurrenceView> {
    const q = id != null ? `?id=${id}` : '';
    return firstValueFrom(this.http.get<DepartmentOccurrenceView>(`${this.base}/occurrence${q}`,
      { headers: this.headers(token) }));
  }

  requestCompletion(token: string, id: number, file: File, message: string): Promise<void> {
    const form = new FormData();
    form.append('id', String(id));
    form.append('file', file);
    if (message.trim()) form.append('message', message.trim());
    return firstValueFrom(this.http.post<void>(`${this.base}/completion-request`, form,
      { headers: this.headers(token) }));
  }
}

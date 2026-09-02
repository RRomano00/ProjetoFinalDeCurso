import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OccurrenceCreateService {
  constructor(private http: HttpClient) {}

  create(occurrence: any): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${environment.api_endpoint}/occurrence`, occurrence));
  }

  async uploadMedia(file: File, type: string): Promise<{ uploadId: string }> {
    const blob = new Blob([await file.arrayBuffer()], { type: file.type });
    const form = new FormData();
    form.append('file', blob, file.name);
    form.append('type', type);
    return firstValueFrom(
      this.http.post<{ uploadId: string }>(`${environment.api_endpoint}/occurrence/upload-media`, form)
    );
  }

  /** Consulta o status do upload (PROCESSING / DONE / REJECTED / ERROR). */
  getUploadStatus(uploadId: string): Promise<any> {
    return firstValueFrom(
      this.http.get<any>(`${environment.api_endpoint}/occurrence/upload-status/${uploadId}`)
    );
  }
}

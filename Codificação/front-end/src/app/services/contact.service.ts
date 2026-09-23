import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ContactService {
  constructor(private http: HttpClient) {}

  send(data: { name: string; email: string; subject: string; message: string }): Promise<any> {
    return firstValueFrom(this.http.post(`${environment.api_endpoint}/contact`, data));
  }
}

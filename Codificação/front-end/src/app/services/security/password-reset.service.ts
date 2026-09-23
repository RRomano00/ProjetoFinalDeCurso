import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PasswordResetService {

  constructor(private http: HttpClient) {}

  requestReset(email: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${environment.api_endpoint}/user/password-reset/request`, { email })
    );
  }

  confirmReset(token: string, newPassword: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${environment.api_endpoint}/user/password-reset/confirm`, { token, newPassword })
    );
  }
}

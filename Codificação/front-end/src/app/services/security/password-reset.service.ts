import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PasswordResetService {

  constructor(private http: HttpClient) {}

  /** Solicita o envio do link/token de recuperação — POST /api/user/password-reset/request */
  requestReset(email: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${environment.api_endpoint}/user/password-reset/request`, { email })
    );
  }

  /** Confirma a nova senha usando o token recebido — POST /api/user/password-reset/confirm */
  confirmReset(token: string, newPassword: string): Promise<any> {
    return firstValueFrom(
      this.http.post(`${environment.api_endpoint}/user/password-reset/confirm`, { token, newPassword })
    );
  }
}

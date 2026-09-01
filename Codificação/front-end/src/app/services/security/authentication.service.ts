import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthenticationService {

  constructor(private http: HttpClient) {}

  /**
   * Step 1 — Login com email + senha.
   * Retorna LoginResponseDto:
   *   { token }                              → login completo
   *   { requiresMfa, mfaToken }             → precisa do código TOTP
   *   { requiresMfaSetup, mfaToken }        → EMPLOYEE/ADMIN precisa configurar 2FA
   */
  authenticate(email: string, password: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate`,
      { email, password },
      { headers: new HttpHeaders({ 'Content-Type': 'application/json' }) }
    );
  }

  /** Step 2a — Enviar código após login (método: 'APP' autenticador ou 'EMAIL') */
  verifyMfa(mfaToken: string, totpCode: string, method: 'APP' | 'EMAIL' = 'APP'): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa`,
      { mfaToken, totpCode, method }
    );
  }

  /** Envia/reenvia o código de verificação por e-mail durante o login */
  sendEmailCode(mfaToken: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa/send-email`,
      { mfaToken }
    );
  }

  /** Step 2b — Buscar QR Code (primeiro login de EMPLOYEE/ADMIN) */
  setupMfa(mfaToken: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa/setup`,
      { mfaToken }
    );
  }

  /** Step 3 — Confirmar QR Code escaneado */
  confirmMfaSetup(mfaToken: string, totpCode: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa/confirm`,
      { mfaToken, totpCode }
    );
  }

  /** Salva os dados do login no localStorage (SEM password) */
  saveSession(token: string, email: string, fullname: string, role: string, id?: string) {
    localStorage.setItem('token',    token);
    localStorage.setItem('email',    email);
    localStorage.setItem('fullname', fullname);
    localStorage.setItem('role',     role);
    if (id) localStorage.setItem('id', id);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isAuthenticated(): boolean {
    return localStorage.getItem('token') != null;
  }

  // ── RF08/RF11: modo visitante (sem conta) ──

  /** Entra como visitante: sem token, apenas leitura + registro anônimo. */
  enterAnonymous() {
    localStorage.clear();
    localStorage.setItem('anonymous', 'true');
  }

  isAnonymous(): boolean {
    return localStorage.getItem('anonymous') === 'true' && !this.isAuthenticated();
  }

  logout() {
    localStorage.clear();
  }
}

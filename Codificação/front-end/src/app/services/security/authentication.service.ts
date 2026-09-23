import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthenticationService {

  constructor(private http: HttpClient) {}

  authenticate(email: string, password: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate`,
      { email, password },
      { headers: new HttpHeaders({ 'Content-Type': 'application/json' }) }
    );
  }

  verifyMfa(mfaToken: string, totpCode: string, method: 'APP' | 'EMAIL' = 'APP'): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa`,
      { mfaToken, totpCode, method }
    );
  }

  sendEmailCode(mfaToken: string): Observable<any> {
    return this.http.post<any>(
      `${environment.authentication_api_endpoint}/authenticate/mfa/send-email`,
      { mfaToken }
    );
  }

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

  /**
   * Sessão vencida é sessão inexistente. Sem conferir o exp, a tela continua
   * "logada" e o back-end — que recusa o token expirado — registra a ocorrência
   * como anônima, sem aviso nenhum.
   */
  isAuthenticated(): boolean {
    const token = this.getToken();
    if (token == null) return false;
    try {
      const part    = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(part));
      if (payload.exp && payload.exp * 1000 > Date.now()) return true;
    } catch { }
    this.endSession();
    return false;
  }

  role(): string { return localStorage.getItem('role') || ''; }

  isVisitor(): boolean { return !this.isAuthenticated(); }

  isCitizen(): boolean { return this.role() === 'CITIZEN'; }

  isStaff(): boolean {
    const r = this.role();
    return r === 'EMPLOYEE' || r === 'ADMINISTRATOR' || r === 'SUPER_ADMIN';
  }

  isSuperAdmin(): boolean { return this.role() === 'SUPER_ADMIN'; }

  isAdmin(): boolean {
    const r = this.role();
    return r === 'ADMINISTRATOR' || r === 'SUPER_ADMIN';
  }

  canSupport(): boolean { return this.isCitizen() || this.isVisitor(); }

  enterAnonymous() {
    this.endSession();
    localStorage.setItem('anonymous', 'true');
  }

  isAnonymous(): boolean {
    return localStorage.getItem('anonymous') === 'true' && !this.isAuthenticated();
  }

  logout() {
    this.endSession();
  }

  /**
   * Encerra a sessão preservando as preferências do navegador. O município em
   * exibição e a dispensa do convite de instalação não são credenciais: a
   * escolha é feita antes de entrar e tem que sobreviver ao logout e ao token
   * vencido — localStorage.clear() apagava justamente isso.
   */
  private endSession() {
    const kept = Object.keys(localStorage)
      .filter(key => key.startsWith('locality.') || key.startsWith('install.'))
      .map(key => [key, localStorage.getItem(key)!] as const);
    localStorage.clear();
    kept.forEach(([key, value]) => localStorage.setItem(key, value));
  }
}

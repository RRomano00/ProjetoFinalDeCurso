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
   *   { requiresMfa, mfaToken }             → precisa do código (app ou e-mail)
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

  /**
   * Sessão vencida é sessão inexistente. Sem conferir o exp, a tela continua
   * "logada" e o back-end — que recusa o token expirado — registra a ocorrência
   * como anônima, sem aviso nenhum. Limpa a sessão morta para que o guard e o
   * `isVisitor` das telas enxerguem a mesma coisa.
   */
  isAuthenticated(): boolean {
    const token = this.getToken();
    if (token == null) return false;
    try {
      const part    = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(part));
      if (payload.exp && payload.exp * 1000 > Date.now()) return true;
    } catch { /* token ilegível: trata como sessão morta */ }
    localStorage.clear();
    return false;
  }

  // ── Papel e permissões da sessão ──
  // Ficavam repetidos como getters em quatro telas, cada uma lendo o
  // localStorage por conta própria (e sem conferir se o token venceu).

  /** Papel do usuário logado; string vazia para quem está sem conta. */
  role(): string { return localStorage.getItem('role') || ''; }

  /** Sem conta: lê e registra ocorrência anônima, mas não apoia. */
  isVisitor(): boolean { return !this.isAuthenticated(); }

  isCitizen(): boolean { return this.role() === 'CITIZEN'; }

  /** Funcionário ou administrador — quem trata ocorrência. */
  isStaff(): boolean {
    const r = this.role();
    return r === 'EMPLOYEE' || r === 'ADMINISTRATOR';
  }

  /** Apoiar é do cidadão; o visitante vê o botão e é convidado a entrar. */
  canSupport(): boolean { return this.isCitizen() || this.isVisitor(); }

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

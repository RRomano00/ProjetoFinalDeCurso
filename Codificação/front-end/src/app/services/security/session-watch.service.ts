import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthenticationService } from './authentication.service';

/**
 * Uma conta, uma sessão. Quando o mesmo login é feito em outro aparelho, o
 * back-end passa a recusar o token desta tela com 401 SESSION_SUPERSEDED: a
 * sessão é encerrada aqui e o aviso no meio da tela pede o login de novo.
 *
 * O pulso existe por causa de quem está com a tela parada — sem ele o aviso só
 * apareceria na próxima ação do usuário, que poderia demorar horas. Vale igual
 * no navegador, no aplicativo instalado no computador e no PWA do celular:
 * quem decide é o servidor, não o aparelho.
 */
@Injectable({ providedIn: 'root' })
export class SessionWatchService {

  /** Comanda o aviso de sessão encerrada exibido pelo MainComponent. */
  readonly ended = signal(false);

  /** ponytail: meio minuto é o atraso máximo do aviso; encurtar só custa tráfego. */
  private static readonly PULSE_MS = 30_000;

  private timer?: ReturnType<typeof setInterval>;

  constructor(private http: HttpClient, private auth: AuthenticationService) {}

  /** Passa a conferir a sessão enquanto houver login nesta tela. */
  start() {
    if (this.timer || !this.auth.isAuthenticated()) return;
    this.timer = setInterval(() => {
      if (!this.auth.isAuthenticated()) { this.stop(); return; }
      this.http.get(`${environment.authentication_api_endpoint}/authenticate/session`)
        .subscribe({ error: () => { /* o interceptor trata o 401 */ } });
    }, SessionWatchService.PULSE_MS);
  }

  stop() {
    clearInterval(this.timer);
    this.timer = undefined;
  }

  /** Chamado pelo interceptor quando o back-end recusa o token desta sessão. */
  end() {
    if (this.ended()) return;
    this.stop();
    this.auth.logout();
    this.ended.set(true);
  }
}

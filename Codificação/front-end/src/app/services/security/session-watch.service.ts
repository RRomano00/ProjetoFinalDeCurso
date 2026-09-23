import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthenticationService } from './authentication.service';

@Injectable({ providedIn: 'root' })
export class SessionWatchService {

  readonly ended = signal(false);

  private static readonly PULSE_MS = 30_000;

  private timer?: ReturnType<typeof setInterval>;

  constructor(private http: HttpClient, private auth: AuthenticationService) {}

  /**
   * O pulso existe por causa de quem está com a tela parada: sem ele, a recusa
   * do token só apareceria na próxima ação do usuário, que pode demorar horas.
   */
  start() {
    if (this.timer || !this.auth.isAuthenticated()) return;
    this.timer = setInterval(() => {
      if (!this.auth.isAuthenticated()) { this.stop(); return; }
      this.http.get(`${environment.authentication_api_endpoint}/authenticate/session`)
        .subscribe({ error: () => { } });
    }, SessionWatchService.PULSE_MS);
  }

  stop() {
    clearInterval(this.timer);
    this.timer = undefined;
  }

  end() {
    if (this.ended()) return;
    this.stop();
    this.auth.logout();
    this.ended.set(true);
  }
}

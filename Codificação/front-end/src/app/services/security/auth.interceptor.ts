import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthenticationService } from './authentication.service';
import { SessionWatchService } from './session-watch.service';

/**
 * Interceptor funcional (Angular 17+).
 * Adiciona o header Authorization: Bearer <token> em todas as requisições.
 * Rotas públicas (authenticate, register, password-reset) não precisam do token
 * mas não é problema enviá-lo — o back-end ignora em rotas públicas.
 *
 * Também é o ponto único onde se descobre que a conta foi acessada em outro
 * aparelho: o back-end recusa o token com 401 SESSION_SUPERSEDED e o aviso de
 * sessão encerrada aparece, venha a recusa do pulso ou de qualquer outra ação.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth    = inject(AuthenticationService);
  const session = inject(SessionWatchService);
  const token   = auth.getToken();

  const handled = token
    ? next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
    : next(req);

  return handled.pipe(tap({
    error: (erro: unknown) => {
      if (erro instanceof HttpErrorResponse && erro.status === 401
          && (erro.error as { reason?: string })?.reason === 'SESSION_SUPERSEDED') {
        session.end();
      }
    }
  }));
};

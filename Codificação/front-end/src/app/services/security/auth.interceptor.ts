import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthenticationService } from './authentication.service';
import { SessionWatchService } from './session-watch.service';

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

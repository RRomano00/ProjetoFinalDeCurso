import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthenticationService } from './authentication.service';
import { SessionWatchService } from './session-watch.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth    = inject(AuthenticationService);
  const session = inject(SessionWatchService);
  const token   = auth.getToken();

  // O link do departamento tem credencial própria; mandar o login de quem usa o navegador
  // poderia derrubar essa sessão (SESSION_SUPERSEDED) sem motivo.
  const departmentLink = req.url.includes('/department-access/');
  const handled = token && !departmentLink
    ? next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
    : next(req);

  return handled.pipe(tap({
    error: (erro: unknown) => {
      const reason = erro instanceof HttpErrorResponse && erro.status === 401
        ? (erro.error as { reason?: string })?.reason : undefined;
      if (reason === 'SESSION_SUPERSEDED' || reason === 'ACCOUNT_REMOVED') session.end(reason);
    }
  }));
};

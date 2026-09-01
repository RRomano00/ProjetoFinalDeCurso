import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthenticationService } from './authentication.service';

/**
 * Interceptor funcional (Angular 17+).
 * Adiciona o header Authorization: Bearer <token> em todas as requisições.
 * Rotas públicas (authenticate, register, password-reset) não precisam do token
 * mas não é problema enviá-lo — o back-end ignora em rotas públicas.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth  = inject(AuthenticationService);
  const token = auth.getToken();

  if (token) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(cloned);
  }

  return next(req);
};

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../authentication.service';

export const authenticationGuard: CanActivateFn = () => {
  const auth   = inject(AuthenticationService);
  const router = inject(Router);
  // Logado OU visitante (RF08/RF11) podem acessar a área do app
  if (auth.isAuthenticated() || auth.isAnonymous()) return true;
  router.navigate(['/account/sign-in']);
  return false;
};

/** RF a.8/b.1: a dashboard de estatísticas é da equipe (Funcionário/Administrador). */
export const staffGuard: CanActivateFn = () => {
  const router = inject(Router);
  const role   = localStorage.getItem('role');
  if (role === 'EMPLOYEE' || role === 'ADMINISTRATOR') return true;
  router.navigate(['/']);
  return false;
};

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../authentication.service';

export const authenticationGuard: CanActivateFn = () => {
  const auth   = inject(AuthenticationService);
  const router = inject(Router);
  if (auth.isAuthenticated()) return true;
  if (auth.isAnonymous() && auth.anonymousMunicipality()) return true;
  // Visitante sem município (ex.: sessão de antes da regra) volta ao login com a escolha aberta.
  router.navigate(['/account/sign-in'], auth.isAnonymous() ? { queryParams: { visitante: 1 } } : {});
  return false;
};

export const staffGuard: CanActivateFn = () => {
  const router = inject(Router);
  if (inject(AuthenticationService).isStaff()) return true;
  router.navigate(['/']);
  return false;
};

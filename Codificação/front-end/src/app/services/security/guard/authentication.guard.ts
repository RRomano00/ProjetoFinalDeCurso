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

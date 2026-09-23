import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../authentication.service';

export const authenticationGuard: CanActivateFn = () => {
  const auth   = inject(AuthenticationService);
  const router = inject(Router);
  if (auth.isAuthenticated() || auth.isAnonymous()) return true;
  router.navigate(['/account/sign-in']);
  return false;
};

export const staffGuard: CanActivateFn = () => {
  const router = inject(Router);
  if (inject(AuthenticationService).isStaff()) return true;
  router.navigate(['/']);
  return false;
};

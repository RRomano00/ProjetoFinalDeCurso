import { Routes } from '@angular/router';
import { authenticationGuard } from './services/security/guard/authentication.guard';

export const routes: Routes = [
  // ── Autenticação (público) ───────────────────────────────────────────────
  {
    path: 'account/sign-in',
    loadComponent: () =>
      import('./views/account/sign-in/sign-in.component').then(m => m.SignInComponent)
  },
  {
    path: 'account/sign-up',
    loadComponent: () =>
      import('./views/account/sign-up/sign-up.component').then(m => m.SignUpComponent)
  },
  {
    path: 'account/recover-password',
    loadComponent: () =>
      import('./views/account/recover-password/recover-password.component').then(m => m.RecoverPasswordComponent)
  },

  // ── App principal (protegido por authenticationGuard) ────────────────────
  {
    path: '',
    loadComponent: () =>
      import('./views/app/main/main.component').then(m => m.MainComponent),
    canActivate: [authenticationGuard],
    children: [
      // Home
      {
        path: '',
        loadComponent: () =>
          import('./views/app/home/home.component').then(m => m.HomeComponent)
      },

      // Ocorrências
      {
        path: 'occurrence/list',
        loadComponent: () =>
          import('./views/occurrences/list-occurrence/list-occurrence.component')
            .then(m => m.ListOccurrenceComponent)
      },
      {
        path: 'occurrence/create',
        loadComponent: () =>
          import('./views/occurrences/create-occurrence/create-occurrence.component')
            .then(m => m.CreateOccurrenceComponent)
      },
      {
        path: 'occurrence/detail/:id',
        loadComponent: () =>
          import('./views/occurrences/detail-occurrence/detail-occurrence.component')
            .then(m => m.DetailOccurrenceComponent)
      },
      {
        path: 'occurrence/statistics',
        loadComponent: () =>
          import('./views/occurrences/statistics/statistics.component')
            .then(m => m.StatisticsComponent)
      },

      // Usuários
      {
        path: 'user/add',
        loadComponent: () =>
          import('./views/user/user-add/user-add.component').then(m => m.UserAddComponent)
      },
      // RF15: gestão de usuários (Administrador)
      {
        path: 'user/list',
        loadComponent: () =>
          import('./views/user/user-list/user-list.component').then(m => m.UserListComponent)
      },
      {
        path: 'account/my-profile',
        loadComponent: () =>
          import('./views/account/my-profile/my-profile.component').then(m => m.MyProfileComponent)
      },

      // Ajuda
      {
        path: 'help',
        loadComponent: () =>
          import('./views/app/help/help.component').then(m => m.HelpComponent)
      },

      // Contato (RF17)
      {
        path: 'contact',
        loadComponent: () =>
          import('./views/app/contact/contact.component').then(m => m.ContactComponent)
      },
    ]
  },

  // 404
  {
    path: '**',
    loadComponent: () =>
      import('./views/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];

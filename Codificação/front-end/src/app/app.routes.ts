import { Routes } from '@angular/router';
import { authenticationGuard, staffGuard } from './services/security/guard/authentication.guard';

export const routes: Routes = [
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

  {
    path: '',
    loadComponent: () =>
      import('./views/app/main/main.component').then(m => m.MainComponent),
    canActivate: [authenticationGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./views/app/home/home.component').then(m => m.HomeComponent)
      },

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
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./views/occurrences/statistics/statistics.component')
            .then(m => m.StatisticsComponent)
      },

      {
        path: 'department/list',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./views/department/department-list/department-list.component')
            .then(m => m.DepartmentListComponent)
      },

      {
        path: 'user/add',
        loadComponent: () =>
          import('./views/user/user-add/user-add.component').then(m => m.UserAddComponent)
      },
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

      {
        path: 'help',
        loadComponent: () =>
          import('./views/app/help/help.component').then(m => m.HelpComponent)
      },

      {
        path: 'contact',
        loadComponent: () =>
          import('./views/app/contact/contact.component').then(m => m.ContactComponent)
      },
    ]
  },

  {
    path: '**',
    loadComponent: () =>
      import('./views/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];

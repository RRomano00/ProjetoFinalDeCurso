import { ApplicationConfig, ErrorHandler, provideZoneChangeDetection, isDevMode } from '@angular/core';
import { provideRouter, withPreloading, PreloadAllModules } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideToastr } from 'ngx-toastr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './services/security/auth.interceptor';
import { provideServiceWorker } from '@angular/service-worker';

class ChunkReloadErrorHandler implements ErrorHandler {
  private static readonly MARCA = 'chunk-reload';

  constructor() {
    setTimeout(() => { try { sessionStorage.removeItem(ChunkReloadErrorHandler.MARCA); } catch { } }, 10000);
  }

  handleError(erro: unknown): void {
    const mensagem = String((erro as { message?: string })?.message ?? erro);
    const pedacoFaltando = /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk \S+ failed/i
      .test(mensagem);
    if (pedacoFaltando) {
      try {
        if (!sessionStorage.getItem(ChunkReloadErrorHandler.MARCA)) {
          sessionStorage.setItem(ChunkReloadErrorHandler.MARCA, '1');
          location.reload();
          return;
        }
      } catch { /* armazenamento bloqueado: segue para o log */ }
    }
    console.error(erro);
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    { provide: ErrorHandler, useClass: ChunkReloadErrorHandler },

    provideRouter(routes, withPreloading(PreloadAllModules)),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideToastr({
      timeOut: 3000,
      positionClass: 'toast-top-right',
      preventDuplicates: true,
    }), provideServiceWorker('ngsw-worker.js', {
            enabled: !isDevMode(),
            registrationStrategy: 'registerWithDelay:3000'
          }),
  ]
};

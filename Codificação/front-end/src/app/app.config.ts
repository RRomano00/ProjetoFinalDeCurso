import { ApplicationConfig, ErrorHandler, provideZoneChangeDetection, isDevMode } from '@angular/core';
import { provideRouter, withPreloading, PreloadAllModules } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideToastr } from 'ngx-toastr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './services/security/auth.interceptor';
import { provideServiceWorker } from '@angular/service-worker';

/**
 * Publicar uma versão nova troca o nome dos arquivos carregados sob demanda. A
 * página que já estava aberta pede um pedaço que não existe mais, e a tela
 * simplesmente não abre — foi o “Failed to fetch dynamically imported module”.
 * Recarregar uma vez resolve; a marca de sessão impede laço quando a falha for
 * de rede, e ela é liberada assim que a aplicação se mantém de pé.
 */
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
    // Cada tela vive num arquivo separado, baixado só quando alguém abre ela.
    // Isso deixava a primeira troca de tela travada esperando a rede. Com o
    // preload, o restante das telas desce em segundo plano logo depois da
    // primeira navegação — quando a pessoa clica, o arquivo já está aqui.
    provideRouter(routes, withPreloading(PreloadAllModules)),
    provideAnimationsAsync(),
    // Interceptor funcional — injeta Bearer token em todos os requests
    provideHttpClient(withInterceptors([authInterceptor])),
    provideToastr({
      timeOut: 3000,
      positionClass: 'toast-top-right',
      preventDuplicates: true,
    }), provideServiceWorker('ngsw-worker.js', {
            enabled: !isDevMode(),
            registrationStrategy: 'registerWhenStable:30000'
          }),
  ]
};

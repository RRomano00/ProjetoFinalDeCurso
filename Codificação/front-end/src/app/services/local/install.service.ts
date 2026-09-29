import { Injectable } from '@angular/core';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

type JanelaComPrompt = Window & { __instalarPrompt?: BeforeInstallPromptEvent | null };

function guardado(): BeforeInstallPromptEvent | null {
  return (window as JanelaComPrompt).__instalarPrompt ?? null;
}

function esquecer(): void {
  (window as JanelaComPrompt).__instalarPrompt = null;
}

/**
 * O Samsung Internet gera o app instalado (WebAPK) para uma versão antiga do
 * Android, e o Play Protect do Android 14+ o bloqueia como "app de risco". O
 * mesmo PWA instalado pelo Chrome passa sem aviso, então a instalação vai para lá.
 */
export function isSamsungInternet(userAgent: string): boolean {
  return /SamsungBrowser/i.test(userAgent);
}

export function chromeIntentUrl(loc: Pick<Location, 'host' | 'pathname' | 'search'>): string {
  return `intent://${loc.host}${loc.pathname}${loc.search}#Intent;scheme=https;package=com.android.chrome;end`;
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', evento => {
    evento.preventDefault();
    (window as JanelaComPrompt).__instalarPrompt = evento as BeforeInstallPromptEvent;
  });
  window.addEventListener('appinstalled', esquecer);
}

@Injectable({ providedIn: 'root' })
export class InstallService {

  private readonly samsung = isSamsungInternet(navigator.userAgent);

  private readonly ios =
    /iphone|ipad|ipod/i.test(navigator.userAgent) && !('onbeforeinstallprompt' in window);

  private get instalado(): boolean {
    return window.matchMedia('(display-mode: standalone)').matches
        || (navigator as unknown as { standalone?: boolean }).standalone === true;
  }

  private static readonly DISPENSA = 'install.dispensado';

  get disponivel(): boolean {
    return !this.instalado && (guardado() !== null || this.ios || this.samsung);
  }

  get convidar(): boolean {
    return this.disponivel && !this.dispensado;
  }

  private get dispensado(): boolean {
    try { return localStorage.getItem(InstallService.DISPENSA) === '1'; }
    catch { return true; }
  }

  dispensar() {
    try { localStorage.setItem(InstallService.DISPENSA, '1'); } catch { }
  }

  async instalar(): Promise<string | null> {
    if (this.samsung) {
      window.location.href = chromeIntentUrl(window.location);
      return 'Abrimos o Fala, Cidade! no Chrome: toque em "Instalar" por lá. '
           + 'Pelo Samsung Internet o Android bloqueia o aplicativo.';
    }
    const prompt = guardado();
    if (this.ios || !prompt) {
      return 'No iPhone, toque em Compartilhar e depois em "Adicionar à Tela de Início".';
    }
    await prompt.prompt();
    await prompt.userChoice;
    esquecer();
    return null;
  }
}

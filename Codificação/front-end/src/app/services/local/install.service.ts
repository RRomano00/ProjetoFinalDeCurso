import { Injectable } from '@angular/core';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

let guardado: BeforeInstallPromptEvent | null = null;

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', evento => {
    // Sem o preventDefault o Chrome mostra a própria faixa e descarta o evento,
    // e aí não sobra nada para o botão da tela acionar.
    evento.preventDefault();
    guardado = evento as BeforeInstallPromptEvent;
  });
  window.addEventListener('appinstalled', () => { guardado = null; });
}

@Injectable({ providedIn: 'root' })
export class InstallService {

  private readonly ios =
    /iphone|ipad|ipod/i.test(navigator.userAgent) && !('onbeforeinstallprompt' in window);

  private get instalado(): boolean {
    return window.matchMedia('(display-mode: standalone)').matches
        || (navigator as unknown as { standalone?: boolean }).standalone === true;
  }

  private static readonly DISPENSA = 'install.dispensado';

  get disponivel(): boolean {
    return !this.instalado && (guardado !== null || this.ios);
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
    if (this.ios || !guardado) {
      return 'No iPhone, toque em Compartilhar e depois em "Adicionar à Tela de Início".';
    }
    await guardado.prompt();
    await guardado.userChoice;
    guardado = null;
    return null;
  }
}

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

  /** Marca da dispensa do convite; sobrevive ao logout, como as demais preferências. */
  private static readonly DISPENSA = 'install.dispensado';

  get disponivel(): boolean {
    return !this.instalado && (guardado !== null || this.ios);
  }

  /** Convite automático: só enquanto a pessoa não tiver dito "agora não". */
  get convidar(): boolean {
    return this.disponivel && !this.dispensado;
  }

  private get dispensado(): boolean {
    try { return localStorage.getItem(InstallService.DISPENSA) === '1'; }
    catch { return true; }   // armazenamento bloqueado: não insistir
  }

  /** "Agora não": o convite some e o botão do menu continua disponível. */
  dispensar() {
    try { localStorage.setItem(InstallService.DISPENSA, '1'); } catch { /* segue sem lembrar */ }
  }

  /**
   * Abre o diálogo de instalação do navegador. No iPhone não existe diálogo a
   * abrir, então devolve a instrução para quem chamou exibir.
   * @returns a instrução do iPhone, ou null quando o navegador se encarregou.
   */
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

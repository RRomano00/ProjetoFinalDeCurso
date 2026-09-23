import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastrService } from 'ngx-toastr';
import { InstallService } from '../services/local/install.service';

@Component({
  selector: 'app-install-invite',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="install-invite" *ngIf="mostrar" role="region" aria-label="Instalar o aplicativo">
      <span class="install-invite-icon" aria-hidden="true">
        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
      </span>
      <div class="install-invite-text">
        <strong>Instale o Fala, Cidade! no seu aparelho</strong>
        <span>Abre direto da tela inicial, sem digitar endereço, e funciona com a rede instável.</span>
      </div>
      <div class="install-invite-actions">
        <button type="button" class="btn-ghost" (click)="dispensar()">Agora não</button>
        <button type="button" class="btn-primary" (click)="instalar()">Instalar</button>
      </div>
    </div>
  `,
  styles: [`
    .install-invite {
      position: fixed;
      left: 1rem;
      right: 1rem;
      bottom: calc(1rem + env(safe-area-inset-bottom, 0px));
      z-index: 1900;
      display: flex;
      align-items: center;
      gap: 0.875rem;
      max-width: 34rem;
      margin: 0 auto;
      padding: 0.875rem 1rem;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-modal);
      box-shadow: var(--lift-high);
    }

    .install-invite-icon {
      display: flex;
      flex: none;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border-radius: var(--r-ctl);
      background: var(--plate);
      color: #fff;
    }

    .install-invite-text { display: flex; flex-direction: column; gap: 0.125rem; min-width: 0; flex: 1; }
    .install-invite-text strong { font-size: 0.9375rem; font-weight: 600; color: var(--ink); }
    .install-invite-text span   { font-size: 0.8125rem; color: var(--ink-mute); line-height: 1.4; }

    .install-invite-actions { display: flex; flex: none; align-items: center; gap: 0.375rem; }

    .btn-primary {
      padding: 0.5rem 1rem;
      background: var(--plate);
      color: #fff;
      border: none;
      border-radius: var(--r-ctl);
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-primary:hover { background: var(--plate-deep); }

    .btn-ghost {
      padding: 0.5rem 0.75rem;
      background: none;
      border: none;
      border-radius: var(--r-ctl);
      font-size: 0.8125rem;
      color: var(--ink-mute);
      cursor: pointer;
    }
    .btn-ghost:hover { background: var(--surface-2); color: var(--ink); }

    @media (max-width: 560px) {
      .install-invite { flex-wrap: wrap; }
      .install-invite-actions { width: 100%; justify-content: flex-end; }
    }
  `]
})
export class InstallInviteComponent {

  private dispensadoAgora = false;

  constructor(private install: InstallService, private toastr: ToastrService) {}

  get mostrar(): boolean {
    return !this.dispensadoAgora && this.install.convidar;
  }

  async instalar() {
    this.dispensadoAgora = true;
    const instrucao = await this.install.instalar();
    if (instrucao) this.toastr.info(instrucao, 'Instalar o aplicativo', { timeOut: 9000 });
  }

  dispensar() {
    this.dispensadoAgora = true;
    this.install.dispensar();
  }
}

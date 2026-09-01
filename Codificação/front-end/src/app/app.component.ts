import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {

  // ── Acessibilidade (RF18): tema claro/escuro + tamanho de fonte ──
  isDark = false;
  /** Tamanhos de fonte base disponíveis (px). O índice 2 (16px) é o padrão. */
  readonly fontSteps = [14, 15, 16, 17, 18, 19];
  readonly defaultFontIndex = 2;
  fontIndex = this.defaultFontIndex;

  constructor(
    private swUpdate: SwUpdate,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.restorePreferences();

    // Avisa o usuário quando o service worker baixou uma nova versão do app (PWA).
    // Só fica ativo em build de produção (no dev o SW vem desabilitado).
    if (this.swUpdate.isEnabled) {
      this.swUpdate.versionUpdates
        .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
        .subscribe(() => {
          this.toastr.info('Uma nova versão está disponível. Recarregando...', 'Fala, Cidade!');
          // Ativa a nova versão e recarrega para aplicá-la.
          this.swUpdate.activateUpdate().then(() => document.location.reload());
        });
    }
  }

  // ── Tema ──
  toggleTheme(): void {
    this.isDark = !this.isDark;
    this.applyTheme();
    localStorage.setItem('pref-theme', this.isDark ? 'dark' : 'light');
  }

  private applyTheme(): void {
    document.documentElement.classList.toggle('dark-theme', this.isDark);
  }

  // ── Tamanho de fonte ──
  increaseFont(): void {
    if (this.fontIndex < this.fontSteps.length - 1) { this.fontIndex++; this.applyFont(); }
  }
  decreaseFont(): void {
    if (this.fontIndex > 0) { this.fontIndex--; this.applyFont(); }
  }
  resetFont(): void {
    this.fontIndex = this.defaultFontIndex;
    this.applyFont();
  }

  private applyFont(): void {
    document.documentElement.style.fontSize = this.fontSteps[this.fontIndex] + 'px';
    localStorage.setItem('pref-font-index', String(this.fontIndex));
  }

  private restorePreferences(): void {
    this.isDark = localStorage.getItem('pref-theme') === 'dark';
    this.applyTheme();

    const savedFont = parseInt(localStorage.getItem('pref-font-index') || '', 10);
    if (!isNaN(savedFont) && savedFont >= 0 && savedFont < this.fontSteps.length) {
      this.fontIndex = savedFont;
    }
    this.applyFont();
  }
}

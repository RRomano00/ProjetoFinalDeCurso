import { Component, OnInit, inject } from '@angular/core';
import { NavigationStart, Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { SwUpdate } from '@angular/service-worker';
import { filter, interval } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {

  isDark = false;
  readonly fontSteps = [14, 15, 16, 17, 18, 19];
  readonly defaultFontIndex = 2;
  fontIndex = this.defaultFontIndex;

  private readonly swUpdate = inject(SwUpdate);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.restorePreferences();
    this.applyNewVersion();
  }
  

  private applyNewVersion(): void {
    if (!this.swUpdate.isEnabled) return;
    const openedAt = Date.now();
    let pending = false;

    this.swUpdate.versionUpdates
      .pipe(filter(e => e.type === 'VERSION_READY'))
      .subscribe(() => {
        if (Date.now() - openedAt < 10_000) document.location.reload();
        else pending = true;
      });
    this.swUpdate.unrecoverable.subscribe(() => document.location.reload());

    this.router.events
      .pipe(filter((e): e is NavigationStart => e instanceof NavigationStart && pending))
      .subscribe(e => document.location.assign(e.url));  // carga completa já na versão nova

    const check = () => this.swUpdate.checkForUpdate().catch(() => {});
    interval(2 * 60_000).subscribe(check);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
  }

  toggleTheme(): void {
    this.isDark = !this.isDark;
    this.applyTheme();
    localStorage.setItem('pref-theme', this.isDark ? 'dark' : 'light');
  }

  private applyTheme(): void {
    document.documentElement.classList.toggle('dark-theme', this.isDark);
  }

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

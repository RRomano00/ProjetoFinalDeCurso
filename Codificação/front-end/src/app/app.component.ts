import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';

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

  ngOnInit(): void {
    this.restorePreferences();
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

import { Directive, HostListener, Input, OnInit, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';

// Preenche o campo com o código copiado (Ctrl+C / celular). Lê ao focar o campo e ao voltar para
// a aba — o caso comum: a pessoa sai para o e-mail/SMS/autenticador, copia e volta.
// Padrão: código de 6 dígitos da verificação em duas etapas.
@Directive({ selector: 'input[appClipboardCode]', standalone: true })
export class ClipboardCodeDirective implements OnInit {
  /** Regex do código aceito; vazio = 6 dígitos. */
  @Input() appClipboardCode = '';

  constructor(@Optional() @Self() private control: NgControl) {}

  ngOnInit() { this.fill(); }

  @HostListener('focus')
  @HostListener('window:focus')
  async fill() {
    if (this.control?.value) return;
    try {
      const copied = (await navigator.clipboard.readText()).trim().toUpperCase();
      if (new RegExp(this.appClipboardCode || '^\\d{6}$').test(copied) && !this.control?.value) {
        this.control?.control?.setValue(copied);
        this.control?.viewToModelUpdate(copied);  // com ngModel, só o setValue não chega à variável
      }
    } catch { }  // sem permissão, sem HTTPS ou navegador sem suporte: segue com o campo vazio
  }
}

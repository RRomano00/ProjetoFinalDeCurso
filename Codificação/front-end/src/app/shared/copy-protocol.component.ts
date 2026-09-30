import { Component, Input } from '@angular/core';
import { ToastrService } from 'ngx-toastr';

// Botão de copiar o protocolo (ou outro código, via label). Para a propagação porque costuma ficar dentro de card/link clicável.
@Component({
  selector: 'app-copy-protocol',
  standalone: true,
  template: `
    <button type="button" class="btn-copy" [class.btn-copy--sm]="small"
            [title]="'Copiar ' + label" [attr.aria-label]="'Copiar o ' + label + ' ' + value"
            (click)="copy($event)" (keydown.enter)="$event.stopPropagation()">
      <svg xmlns="http://www.w3.org/2000/svg" [attr.width]="small ? 12 : 15" [attr.height]="small ? 12 : 15"
        viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
      </svg>
    </button>
  `,
  styles: [':host { display: inline-flex; vertical-align: middle; }']
})
export class CopyProtocolComponent {
  @Input({ required: true }) value?: string;
  @Input() small = false;
  @Input() label = 'protocolo';

  constructor(private toastr: ToastrService) {}

  async copy(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(this.value || '');
      this.toastr.success(`${this.label[0].toUpperCase() + this.label.slice(1)} ${this.value} copiado.`);
    } catch { this.toastr.error(`Não foi possível copiar o ${this.label}.`); }
  }
}

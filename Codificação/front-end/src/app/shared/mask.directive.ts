import { Directive, ElementRef, HostBinding, HostListener, Input, OnInit, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';
import { maskCep, maskPhone, maskUf } from './br-mask';

type MaskKind = 'cep' | 'phone' | 'uf';
const MASKS: Record<MaskKind, { fn: (v: string) => string; max: number; mode: string }> = {
  cep:   { fn: maskCep,   max: 9,  mode: 'numeric' },
  phone: { fn: maskPhone, max: 15, mode: 'tel' },
  uf:    { fn: maskUf,    max: 2,  mode: 'text' },
};

// Formata o campo enquanto a pessoa digita (e o valor que vem do banco, ao carregar).
@Directive({ selector: 'input[appMask]', standalone: true })
export class MaskDirective implements OnInit {
  @Input({ required: true }) appMask!: MaskKind;

  @HostBinding('attr.maxlength') get maxLength() { return MASKS[this.appMask].max; }
  @HostBinding('attr.inputmode') get inputMode() { return MASKS[this.appMask].mode; }

  constructor(private el: ElementRef<HTMLInputElement>, @Optional() @Self() private control: NgControl) {}

  ngOnInit() {
    this.control?.valueChanges?.subscribe(v => this.apply(v ?? ''));
  }

  @HostListener('input')
  onInput() { this.apply(this.el.nativeElement.value); }

  private apply(value: string) {
    const masked = MASKS[this.appMask].fn(String(value));
    if (masked === value && this.el.nativeElement.value === masked) return;
    this.el.nativeElement.value = masked;
    if (this.control?.control && this.control.value !== masked) {
      this.control.control.setValue(masked, { emitEvent: false });
      this.control.viewToModelUpdate(masked);  // com ngModel, só o setValue não chega à variável
    }
  }
}

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LocalityPreferenceService, Municipality } from '../services/local/locality-preference.service';
import { LocalityService, CityOptions, FederativeUnit } from '../services/local/locality.service';
import { MaskDirective } from './mask.directive';

// Escolha obrigatória do município do visitante: ao entrar como anônimo e no "Alterar município".
@Component({
  selector: 'app-municipality-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MaskDirective],
  template: `
    <div class="modal-overlay modal-overlay--blur">
      <form class="modal-card" role="dialog" aria-modal="true" aria-labelledby="municipio-titulo"
            [formGroup]="form" (ngSubmit)="confirm()">
        <h2 id="municipio-titulo">Qual é o seu município?</h2>
        <p>O mapa e as ocorrências são mostrados para ele.</p>

        <button type="button" class="btn-outline" (click)="useMyLocation()" [disabled]="locating">
          @if (locating) {Localizando<span class="loading-dots" aria-hidden="true"></span>} @else { {{ '📍 Usar minha localização' }} }
        </button>

        <div class="field-row">
          <div class="field-group">
            <label for="md-state">UF</label>
            <input type="text" id="md-state" formControlName="state" appMask="uf" list="md-uf-options"
                   placeholder="MG" autocomplete="address-level1"
                   autocapitalize="characters" spellcheck="false">
            <datalist id="md-uf-options">
              <option *ngFor="let u of units" [value]="u.uf">{{ u.name }}</option>
            </datalist>
          </div>
          <div class="field-group">
            <label for="md-city">Município</label>
            <input type="text" id="md-city" formControlName="city" list="md-city-options" maxlength="100"
                   [readOnly]="!cityOptions.ready" autocomplete="address-level2" spellcheck="false"
                   [placeholder]="cityOptions.ready ? 'Comece a digitar' : 'Informe a UF primeiro'">
            <datalist id="md-city-options">
              <option *ngFor="let c of cityOptions.list" [value]="c"></option>
            </datalist>
          </div>
        </div>
        <span class="field-error" *ngIf="form.controls.state.touched && form.controls.state.invalid">Informe a UF.</span>
        <span class="field-error" *ngIf="form.controls.city.touched && form.controls.city.hasError('required')">
          Informe o município.
        </span>
        <span class="field-error" *ngIf="form.controls.city.hasError('unknown')">
          Município não encontrado nessa UF. Escolha um da lista.
        </span>

        <div class="modal-actions">
          <button type="button" class="btn-ghost" (click)="cancelled.emit()">{{ cancelLabel }}</button>
          <button type="submit" class="btn-solid">{{ confirmLabel }}</button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .modal-card { max-width: 440px; text-align: left; }
    .field-row { display: grid; grid-template-columns: 5.5rem 1fr; gap: 0.75rem; }
    .field-group { display: flex; flex-direction: column; gap: 0.375rem; }
    label { font-size: 0.875rem; font-weight: 500; color: var(--ink); }
    input {
      width: 100%; box-sizing: border-box; padding: 0.6875rem 0.875rem;
      border: 1px solid var(--line); border-radius: var(--r-ctl);
      font-size: 1rem; color: var(--ink); background: var(--surface);
    }
    input:focus { outline: none; border-color: var(--plate); box-shadow: 0 0 0 3px var(--plate-wash); }
    .field-error { font-size: 0.8125rem; color: var(--st-refused); }
    button { padding: 0.625rem 1.125rem; border-radius: var(--r-ctl); font-size: 0.9375rem; font-weight: 500; cursor: pointer; }
    .btn-outline { border: 1px solid var(--line); background: var(--surface); color: var(--ink); }
    .btn-outline:hover:not(:disabled) { background: var(--plate-wash); border-color: var(--plate); color: var(--plate); }
    .btn-solid { border: 1px solid var(--plate); background: var(--plate); color: #fff; font-weight: 600; }
    .btn-solid:hover { background: var(--plate-deep); }
    .btn-ghost { border: none; background: transparent; color: var(--ink-mute); }
    .btn-ghost:hover { color: var(--plate); text-decoration: underline; }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
    .modal-actions { align-items: center; }
  `]
})
export class MunicipalityDialogComponent implements OnInit {
  @Input() initial: Municipality | null = null;
  @Input() confirmLabel = 'Confirmar';
  @Input() cancelLabel = 'Voltar';
  @Output() chosen = new EventEmitter<Municipality>();
  @Output() cancelled = new EventEmitter<void>();

  form = new FormGroup({
    state: new FormControl('', Validators.required),
    city:  new FormControl('', Validators.required),
  });
  units: FederativeUnit[];
  cityOptions: CityOptions;
  locating = false;

  constructor(private localities: LocalityService, private locality: LocalityPreferenceService) {
    this.units = localities.units;
    this.cityOptions = localities.bindCityToUf(this.form);
  }

  ngOnInit() {
    if (this.initial) this.form.setValue({ state: this.initial.state, city: this.initial.city });
  }

  async useMyLocation() {
    this.locating = true;
    try {
      const m = await this.locality.detect(true);
      if (m) this.form.setValue({ state: m.state, city: m.city });
    } finally { this.locating = false; }
  }

  async confirm() {
    this.form.markAllAsTouched();
    const uf = this.localities.normalizeUf(this.form.value.state);
    const typed = (this.form.value.city || '').trim();
    if (!uf || !typed) return;
    // Nome oficial do IBGE; se a lista não carregar, aceita o que foi digitado.
    const list = await this.localities.cities(uf);
    const city = list.length
      ? list.find(c => c.localeCompare(typed, 'pt-BR', { sensitivity: 'base' }) === 0)
      : typed;
    if (!city) { this.form.controls.city.setErrors({ unknown: true }); return; }
    this.chosen.emit({ city, state: uf });
  }
}

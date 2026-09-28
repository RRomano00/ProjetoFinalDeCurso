import { Component, ElementRef } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { UserCreateService } from '../../../services/user/user-create.service';
import { LocalityService, CityOptions } from '../../../services/local/locality.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';
import { brBirthDateValidator, brDateToIso, maskBrDate } from '../../../shared/br-date';

@Component({
  selector: 'app-sign-up',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective],
  templateUrl: './sign-up.component.html',
  styleUrls: ['../auth-shell.css', './sign-up.component.css']
})
export class SignUpComponent {
  form!: FormGroup;
  loading = false;
  showTerms = false;

  // Uma pergunta por tela: o formulário é um só, as etapas só escolhem o que aparece.
  readonly steps = [
    { title: 'Como você se chama?',  fields: ['fullname'] },
    { title: 'Qual é o seu e-mail?', fields: ['email'] },
    { title: 'Quando você nasceu?',  fields: ['dateOfBirth', 'phoneNumber'] },
    { title: 'Onde você mora?',      fields: ['state', 'city'] },
    { title: 'Crie sua senha',       fields: ['password', 'repeatPassword'] },
    { title: 'Quase lá!',            fields: ['acceptsTerms', 'mfaEmailEnabled'] },
  ];
  private readonly EMAIL_STEP = 1;
  private readonly PHONE_STEP = 2;
  private readonly PASSWORD_STEP = 4;
  step = 0;

  get isLastStep(): boolean { return this.step === this.steps.length - 1; }
  get progress(): number { return ((this.step + 1) / this.steps.length) * 100; }

  get passwordRules(): { label: string; ok: boolean }[] {
    const p: string = this.form.value.password || '';
    return [
      { label: '8 ou mais caracteres', ok: p.length >= 8 },
      { label: 'Uma letra', ok: /[A-Za-z]/.test(p) },
      { label: 'Um número', ok: /\d/.test(p) },
      { label: 'Um caractere especial (@$!%*#?&)', ok: /[@$!%*#?&]/.test(p) },
    ];
  }

  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  constructor(
    private fb: FormBuilder,
    private userCreateService: UserCreateService,
    private locality: LocalityService,
    private toastr: ToastrService,
    private router: Router,
    private host: ElementRef<HTMLElement>
  ) {
    this.form = this.fb.group({
      fullname:       ['', [Validators.required, Validators.minLength(2)]],
      email:          ['', [Validators.required, Validators.email]],
      dateOfBirth:    ['', [Validators.required, brBirthDateValidator]],
      phoneNumber:    [''],
      state:          ['', [Validators.required]],
      city:           ['', [Validators.required]],
      password:       ['', [Validators.required, Validators.minLength(8),
                            Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)]],
      repeatPassword: ['', Validators.required],
      acceptsTerms:   [false, Validators.requiredTrue],
      mfaEmailEnabled: [false]
    });

    const dob = this.form.controls['dateOfBirth'];
    dob.valueChanges.subscribe(v => {
      const masked = maskBrDate(v);
      if (masked !== v) dob.setValue(masked, { emitEvent: false });
    });

    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);
  }

  private stepValid(i: number): boolean {
    return this.steps[i].fields.every(f => this.form.controls[f].valid)
        && (i !== this.PASSWORD_STEP || this.passwordsMatch());
  }

  next() {
    if (this.loading) return;
    if (!this.stepValid(this.step)) {
      this.steps[this.step].fields.forEach(f => this.form.controls[f].markAsTouched());
      return;
    }
    if (this.isLastStep) { this.create(); return; }
    this.goTo(this.step + 1);
  }

  // Na primeira etapa, voltar é desistir do cadastro: leva de volta ao login.
  back() {
    if (this.step > 0) this.goTo(this.step - 1);
    else this.router.navigate(['/account/sign-in']);
  }

  private goTo(i: number) {
    this.step = i;
    // Espera a etapa nova renderizar para pôr o cursor no primeiro campo.
    setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>('.step.active input')?.focus());
  }

  passwordsMatch(): boolean {
    return this.form.value.password === this.form.value.repeatPassword;
  }

  openTerms(event: Event) {
    event.preventDefault();
    this.showTerms = true;
  }

  closeTerms() {
    this.showTerms = false;
  }

  acceptTerms() {
    this.form.patchValue({ acceptsTerms: true });
    this.showTerms = false;
  }

  create() {
    const invalid = this.steps.findIndex((_, i) => !this.stepValid(i));
    if (invalid >= 0) { this.goTo(invalid); this.form.markAllAsTouched(); return; }

    this.loading = true;
    this.userCreateService.registerCitizen({
      fullname:     this.form.value.fullname,
      email:        this.form.value.email,
      dateOfBirth:  brDateToIso(this.form.value.dateOfBirth)!,
      phoneNumber:  this.form.value.phoneNumber?.trim() || undefined,
      state:        this.locality.normalizeUf(this.form.value.state) ?? undefined,
      city:         this.form.value.city?.trim(),
      password:     this.form.value.password,
      acceptsTerms: true,
      mfaEmailEnabled: !!this.form.value.mfaEmailEnabled
    }).subscribe({
      next: () => {
        this.loading = false;
        this.toastr.success('Conta criada com sucesso! Você receberá um e-mail de boas-vindas.');
        this.router.navigate(['/account/sign-in']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 409) {
          // E-mail e celular só são conferidos no envio: volta para a etapa do campo repetido.
          const msg: string = err.error?.error || 'Este e-mail já está cadastrado.';
          const phone = err.error?.field === 'phoneNumber';
          const field = phone ? 'phoneNumber' : 'email';
          this.form.controls[field].setErrors({ taken: true });
          this.form.controls[field].markAsTouched();
          this.goTo(phone ? this.PHONE_STEP : this.EMAIL_STEP);
          this.toastr.error(msg);
        } else if (err.status === 400) {
          this.toastr.error('Verifique os dados informados. A senha deve ter 8+ caracteres, letra, número e caractere especial.');
        } else {
          this.toastr.error('Erro ao criar conta. Tente novamente.');
        }
      }
    });
  }
}

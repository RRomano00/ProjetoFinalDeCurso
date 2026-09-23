import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { UserCreateService } from '../../../services/user/user-create.service';
import { LocalityService, CityOptions } from '../../../services/local/locality.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';

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

  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  constructor(
    private fb: FormBuilder,
    private userCreateService: UserCreateService,
    private locality: LocalityService,
    private toastr: ToastrService,
    private router: Router
  ) {
    this.form = this.fb.group({
      fullname:       ['', [Validators.required, Validators.minLength(2)]],
      email:          ['', [Validators.required, Validators.email]],
      dateOfBirth:    ['', [Validators.required]],
      phoneNumber:    [''],
      state:          ['', [Validators.required]],
      city:           ['', [Validators.required]],
      password:       ['', [Validators.required, Validators.minLength(8),
                            Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)]],
      repeatPassword: ['', Validators.required],
      acceptsTerms:   [false, Validators.requiredTrue],
      mfaEmailEnabled: [false]
    });

    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);
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
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (!this.passwordsMatch()) { this.toastr.error('As senhas não coincidem!'); return; }

    this.loading = true;
    this.userCreateService.registerCitizen({
      fullname:     this.form.value.fullname,
      email:        this.form.value.email,
      dateOfBirth:  this.form.value.dateOfBirth,
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
          this.toastr.error(err.error?.error || 'Este e-mail já está cadastrado.');
        } else if (err.status === 400) {
          this.toastr.error('Verifique os dados informados. A senha deve ter 8+ caracteres, letra, número e caractere especial.');
        } else {
          this.toastr.error('Erro ao criar conta. Tente novamente.');
        }
      }
    });
  }
}

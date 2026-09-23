import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { UserCreateService } from '../../../services/user/user-create.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';
import { LocalityService, CityOptions } from '../../../services/local/locality.service';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { UserReadService } from '../../../services/user/user-read.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-user-add',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective],
  templateUrl: './user-add.component.html',
  styleUrl: './user-add.component.css'
})
export class UserAddComponent implements OnInit {
  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  form!: FormGroup;
  loading = false;

  get isSuperAdmin() { return this.auth.isSuperAdmin(); }

  roles: { value: string; label: string }[] = [];

  get isSuperAdminSelected() { return this.form.value.role === 'SUPER_ADMIN'; }

  get localityLocked() { return !this.isSuperAdmin; }

  constructor(
    private fb: FormBuilder,
    private userCreateService: UserCreateService,
    private toastr: ToastrService,
    private router: Router,
    private locality: LocalityService,
    private auth: AuthenticationService,
    private userReadService: UserReadService
  ) {
    this.form = this.fb.group({
      fullname:       ['', [Validators.required, Validators.minLength(2)]],
      email:          ['', [Validators.required, Validators.email]],
      state:          ['', [Validators.required]],
      city:           ['', [Validators.required]],
      password:       ['', [Validators.required, Validators.minLength(8),
                            Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)]],
      repeatPassword: ['', Validators.required],
      role:           ['EMPLOYEE', Validators.required]
    });
    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);

    this.roles = [{ value: 'EMPLOYEE',      label: 'Funcionário' },
                  { value: 'ADMINISTRATOR', label: 'Administrador do município' }];
    if (this.isSuperAdmin) this.roles.push({ value: 'SUPER_ADMIN', label: 'Super Administrador' });

    this.form.get('role')!.valueChanges.subscribe(role => this.applyRoleRules(role));
  }

  async ngOnInit() {
    if (this.isSuperAdmin) return;
    const id = localStorage.getItem('id');
    if (!id) return;
    try {
      const me = await this.userReadService.findById(id);
      if (me?.state) this.form.patchValue({ state: me.state });
      if (me?.city)  this.form.patchValue({ city: me.city });
    } catch { }
  }

  private applyRoleRules(role: string) {
    const state = this.form.get('state')!, city = this.form.get('city')!;
    if (role === 'SUPER_ADMIN') {
      state.clearValidators(); city.clearValidators();
      state.setValue('', { emitEvent: false }); city.setValue('', { emitEvent: false });
    } else {
      state.setValidators([Validators.required]); city.setValidators([Validators.required]);
    }
    state.updateValueAndValidity({ emitEvent: false });
    city.updateValueAndValidity({ emitEvent: false });
  }

  passwordsMatch(): boolean {
    return this.form.value.password === this.form.value.repeatPassword;
  }

  create() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (!this.passwordsMatch()) { this.toastr.error('As senhas não coincidem!'); return; }

    this.loading = true;
    this.userCreateService.createStaff({
      fullname: this.form.value.fullname,
      email:    this.form.value.email,
      city:     this.form.value.city,
      state:    this.locality.normalizeUf(this.form.value.state) ?? undefined,
      password: this.form.value.password,
      role:     this.form.value.role
    }).subscribe({
      next: () => {
        this.loading = false;
        this.toastr.success('Usuário criado! No primeiro login, ele recebe um código de 2FA por e-mail.');
        this.form.reset({ role: 'EMPLOYEE' });
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 403) {
          this.toastr.error(err.error?.error
            || 'Sem permissão para criar esta conta.');
        } else if (err.status === 400) {
          this.toastr.error('Dados inválidos. Senha deve ter 8+ caracteres, letra, número e especial.');
        } else if (err.status === 409) {
          this.toastr.error(err.error?.error || 'Este e-mail já está cadastrado.');
        } else {
          this.toastr.error('Erro ao criar usuário. Tente novamente.');
        }
      }
    });
  }
}

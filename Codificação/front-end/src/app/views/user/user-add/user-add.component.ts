import { Component } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { UserCreateService } from '../../../services/user/user-create.service';
import { ToastrService } from 'ngx-toastr';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-user-add',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './user-add.component.html',
  styleUrl: './user-add.component.css'
})
export class UserAddComponent {
  form!: FormGroup;
  loading = false;

  // Roles disponíveis para criação via painel admin
  roles = [
    { value: 'EMPLOYEE',      label: 'Funcionário' },
    { value: 'ADMINISTRATOR', label: 'Administrador' }
  ];

  constructor(
    private fb: FormBuilder,
    private userCreateService: UserCreateService,
    private toastr: ToastrService,
    private router: Router
  ) {
    this.form = this.fb.group({
      fullname:       ['', [Validators.required, Validators.minLength(2)]],
      email:          ['', [Validators.required, Validators.email]],
      city:           ['', [Validators.required]],
      password:       ['', [Validators.required, Validators.minLength(8),
                            Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)]],
      repeatPassword: ['', Validators.required],
      role:           ['EMPLOYEE', Validators.required]
    });
  }

  passwordsMatch(): boolean {
    return this.form.value.password === this.form.value.repeatPassword;
  }

  create() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (!this.passwordsMatch()) { this.toastr.error('As senhas não coincidem!'); return; }

    this.loading = true;
    // Usa o endpoint correto /api/user/employee (requer token de ADMINISTRATOR)
    this.userCreateService.createStaff({
      fullname: this.form.value.fullname,
      email:    this.form.value.email,
      city:     this.form.value.city,
      password: this.form.value.password,
      role:     this.form.value.role
    }).subscribe({
      next: () => {
        this.loading = false;
        this.toastr.success('Usuário criado! No primeiro login, ele deverá configurar o 2FA.');
        this.form.reset({ role: 'EMPLOYEE' });
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 403) {
          this.toastr.error('Sem permissão. Apenas administradores podem criar funcionários.');
        } else if (err.status === 400) {
          this.toastr.error('Dados inválidos. Senha deve ter 8+ caracteres, letra, número e especial.');
        } else {
          this.toastr.error('Erro ao criar usuário. Tente novamente.');
        }
      }
    });
  }
}

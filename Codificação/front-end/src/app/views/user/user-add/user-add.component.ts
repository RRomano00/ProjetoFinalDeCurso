import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { UserCreateService } from '../../../services/user/user-create.service';
import { UserReadService } from '../../../services/user/user-read.service';
import { ToastrService } from 'ngx-toastr';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-user-add',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './user-add.component.html',
  styleUrl: './user-add.component.css'
})
export class UserAddComponent implements OnInit {
  form!: FormGroup;
  loading = false;

  /**
   * Municípios já em uso, sugeridos no <datalist> do campo Município.
   * O backend filtra as ocorrências por igualdade exata (WHERE o.city = ?),
   * então digitar "Santa Rita" no lugar de "Santa Rita do Sapucaí" faz o
   * funcionário não enxergar ocorrência nenhuma. Sugerir o que já existe evita
   * o erro sem impedir o cadastro de um município novo.
   */
  cities: string[] = [];

  // Roles disponíveis para criação via painel admin
  roles = [
    { value: 'EMPLOYEE',      label: 'Funcionário' },
    { value: 'ADMINISTRATOR', label: 'Administrador' }
  ];

  constructor(
    private fb: FormBuilder,
    private userCreateService: UserCreateService,
    private userReadService: UserReadService,
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

  async ngOnInit(): Promise<void> {
    try {
      const users = await this.userReadService.findAll();
      // Só usuários ativos: contas inativas costumam ser testes antigos e
      // arrastariam municípios errados para dentro das sugestões.
      // O trim() é essencial: já existe cadastro com "Santa Rita " (espaço no
      // fim), que viraria uma sugestão duplicada e visualmente idêntica.
      const counts = new Map<string, number>();
      for (const u of users ?? []) {
        if (!u?.active || typeof u?.city !== 'string') continue;
        const city = u.city.trim();
        if (city) counts.set(city, (counts.get(city) ?? 0) + 1);
      }
      // Mais usados primeiro: o município correto fica no topo da lista, e as
      // variantes digitadas errado (menos frequentes) ficam para baixo.
      this.cities = [...counts.entries()]
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'pt-BR'))
        .map(([city]) => city);
    } catch {
      this.cities = []; // sem sugestões o campo segue funcionando como texto livre
    }
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
        this.toastr.success('Usuário criado! No primeiro login, ele recebe um código de 2FA por e-mail.');
        this.form.reset({ role: 'EMPLOYEE' });
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 403) {
          this.toastr.error('Sem permissão. Apenas administradores podem criar funcionários.');
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

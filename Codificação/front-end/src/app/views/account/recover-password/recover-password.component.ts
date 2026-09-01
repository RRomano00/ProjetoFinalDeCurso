import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { PasswordResetService } from '../../../services/security/password-reset.service';
import { ToastrService } from 'ngx-toastr';

type ResetStep = 'request' | 'confirm';

@Component({
  selector: 'app-recover-password',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './recover-password.component.html',
  styleUrl: './recover-password.component.css'
})
export class RecoverPasswordComponent {
  step: ResetStep = 'request';
  loading = false;

  // Etapa 1 — solicitar
  email = new FormControl('', [Validators.required, Validators.email]);

  // Etapa 2 — confirmar com token
  token = new FormControl('', [Validators.required]);
  newPassword = new FormControl('', [
    Validators.required, Validators.minLength(8),
    Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)
  ]);

  constructor(
    private resetService: PasswordResetService,
    private toastr: ToastrService,
    private router: Router
  ) {}

  async requestReset() {
    if (this.email.invalid) { this.email.markAsTouched(); return; }
    this.loading = true;
    try {
      await this.resetService.requestReset(this.email.value!);
      this.toastr.success('Se o e-mail existir, enviamos um código de recuperação.');
      this.step = 'confirm';
    } catch {
      // Por segurança, a resposta é sempre positiva mesmo se o e-mail não existir
      this.toastr.success('Se o e-mail existir, enviamos um código de recuperação.');
      this.step = 'confirm';
    } finally {
      this.loading = false;
    }
  }

  async confirmReset() {
    if (this.token.invalid || this.newPassword.invalid) {
      this.token.markAsTouched();
      this.newPassword.markAsTouched();
      return;
    }
    this.loading = true;
    try {
      await this.resetService.confirmReset(this.token.value!, this.newPassword.value!);
      this.toastr.success('Senha redefinida com sucesso! Faça login.');
      this.router.navigate(['/account/sign-in']);
    } catch {
      this.toastr.error('Token inválido ou expirado. Solicite um novo.');
    } finally {
      this.loading = false;
    }
  }
}

import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { PasswordResetService } from '../../../services/security/password-reset.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';

type ResetStep = 'request' | 'code' | 'password';

@Component({
  selector: 'app-recover-password',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective],
  templateUrl: './recover-password.component.html',
  styleUrls: ['../auth-shell.css', './recover-password.component.css']
})
export class RecoverPasswordComponent {
  step: ResetStep = 'request';
  loading = false;

  email = new FormControl('', [Validators.required, Validators.email]);

  token = new FormControl('', [Validators.required]);
  newPassword = new FormControl('', [
    Validators.required, Validators.minLength(8),
    Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)
  ]);
  confirmPassword = new FormControl('', [Validators.required]);

  passwordsMatch(): boolean {
    return this.newPassword.value === this.confirmPassword.value;
  }

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
    } catch { }
    // Mesma resposta com ou sem conta: a tela não revela quais e-mails existem.
    this.toastr.success('Se o e-mail existir, enviamos um código de recuperação.');
    this.step = 'code';
    this.loading = false;
  }

  async verifyCode() {
    if (this.token.invalid) { this.token.markAsTouched(); return; }
    this.loading = true;
    try {
      await this.resetService.verifyToken(this.token.value!);
      this.step = 'password';
    } catch {
      this.toastr.error('Código inválido ou expirado. Solicite um novo.');
    } finally {
      this.loading = false;
    }
  }

  async confirmReset() {
    if (this.newPassword.invalid || this.confirmPassword.invalid) {
      this.newPassword.markAsTouched();
      this.confirmPassword.markAsTouched();
      return;
    }
    if (!this.passwordsMatch()) {
      this.confirmPassword.markAsTouched();
      this.toastr.error('As senhas não coincidem!');
      return;
    }
    this.loading = true;
    try {
      await this.resetService.confirmReset(this.token.value!, this.newPassword.value!);
      this.toastr.success('Senha redefinida com sucesso! Faça login.');
      this.router.navigate(['/account/sign-in']);
    } catch {
      // O código venceu entre a conferência e o envio da senha.
      this.toastr.error('Código inválido ou expirado. Solicite um novo.');
      this.step = 'code';
    } finally {
      this.loading = false;
    }
  }
}

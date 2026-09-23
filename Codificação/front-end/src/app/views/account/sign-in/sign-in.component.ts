import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { LocalityPreferenceService } from '../../../services/local/locality-preference.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';
import { InstallInviteComponent } from '../../../shared/install-invite.component';

type LoginStep = 'credentials' | 'mfa-select' | 'mfa-verify';
type MfaMethod = 'APP' | 'EMAIL';

@Component({
  selector: 'app-sign-in',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective,
            InstallInviteComponent],
  templateUrl: './sign-in.component.html',
  styleUrls: ['../auth-shell.css', './sign-in.component.css']
})
export class SignInComponent implements OnInit, OnDestroy {
  step: LoginStep = 'credentials';

  email    = new FormControl('', [Validators.required, Validators.email]);
  password = new FormControl('', [Validators.required]);
  isLoginIncorrect = false;
  loading = false;

  totpCode  = new FormControl('', [Validators.required, Validators.pattern(/^\d{6}$/)]);
  mfaToken  = '';
  mfaError  = false;

  mfaAppAvailable   = false;
  mfaEmailAvailable = false;
  mfaMethod: MfaMethod = 'APP';

  resendCountdown = 0;
  private resendTimer?: any;

  constructor(
    private router: Router,
    private auth: AuthenticationService,
    private locality: LocalityPreferenceService,
    private toastr: ToastrService
  ) {}

  async ngOnInit() {
    if (this.auth.isAuthenticated()) { this.router.navigate(['']); return; }
    this.askLocation();
  }

  private async askLocation() {
    if (await this.locality.deniedBefore()) return;
    this.locality.detect();
  }

  enterAnonymous() {
    this.auth.enterAnonymous();
    this.router.navigate(['']);
  }

  ngOnDestroy() { this.clearResendTimer(); }

  login() {
    if (this.email.invalid || this.password.invalid) return;
    this.loading = true;
    this.isLoginIncorrect = false;

    this.auth.authenticate(this.email.value!, this.password.value!).subscribe({
      next: (res: any) => {
        this.loading = false;
        if (res.token) { this.finishLogin(res.token); return; }

        this.mfaToken = res.mfaToken;

        if (res.requiresMfa) {
          this.mfaAppAvailable   = !!res.mfaAppAvailable;
          this.mfaEmailAvailable = !!res.mfaEmailAvailable;

          if (this.mfaAppAvailable && this.mfaEmailAvailable) {
            this.step = 'mfa-select';
          } else if (this.mfaEmailAvailable) {
            this.mfaMethod = 'EMAIL';
            this.totpCode.reset();
            this.step = 'mfa-verify';
            this.startResendCountdown();
          } else {
            this.mfaMethod = 'APP';
            this.totpCode.reset();
            this.step = 'mfa-verify';
          }
        }
      },
      error: (e) => {
        this.loading = false;
        if (e?.status === 401 || e?.status === 403) {
          this.isLoginIncorrect = true;
          this.toastr.error('Email e/ou senha incorretos.');
        } else {
          this.toastr.error(e?.status
            ? `Nao foi possivel entrar agora (erro ${e.status}). Tente de novo em instantes.`
            : 'Sem conexao com o servidor. Verifique a internet e tente de novo.');
        }
      }
    });
  }

  chooseMethod(method: MfaMethod) {
    if (this.loading) return;
    this.mfaMethod = method;
    this.mfaError = false;
    this.totpCode.reset();

    if (method === 'EMAIL') {
      this.loading = true;
      this.auth.sendEmailCode(this.mfaToken).subscribe({
        next: () => {
          this.loading = false;
          this.toastr.info('Enviamos um código para o seu e-mail.');
          this.step = 'mfa-verify';
          this.startResendCountdown();
        },
        error: () => {
          this.loading = false;
          this.toastr.error('Não foi possível enviar o código por e-mail.');
        }
      });
    } else {
      this.step = 'mfa-verify';
    }
  }

  verifyMfa() {
    if (this.totpCode.invalid) return;
    this.mfaError = false;
    this.loading  = true;
    this.auth.verifyMfa(this.mfaToken, this.totpCode.value!, this.mfaMethod).subscribe({
      next: (res: any) => { this.loading = false; this.finishLogin(res.token); },
      error: () => {
        this.loading  = false;
        this.mfaError = true;
        this.toastr.error('Código inválido ou expirado.');
      }
    });
  }

  resendCode() {
    if (this.resendCountdown > 0) return;
    this.auth.sendEmailCode(this.mfaToken).subscribe({
      next: () => {
        this.toastr.success('Novo código enviado para o seu e-mail.');
        this.startResendCountdown();
      },
      error: () => this.toastr.error('Falha ao reenviar o código.')
    });
  }

  private startResendCountdown() {
    this.clearResendTimer();
    this.resendCountdown = 15;
    this.resendTimer = setInterval(() => {
      this.resendCountdown--;
      if (this.resendCountdown <= 0) this.clearResendTimer();
    }, 1000);
  }

  private clearResendTimer() {
    if (this.resendTimer) { clearInterval(this.resendTimer); this.resendTimer = undefined; }
  }

  backToLogin() {
    this.clearResendTimer();
    this.step = 'credentials';
    this.mfaError = false;
    this.totpCode.reset();
    this.password.reset();
    this.resendCountdown = 0;
  }

  private finishLogin(token: string) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.auth.saveSession(token, payload.email, payload.fullname, payload.role,
                            payload.id != null ? String(payload.id) : undefined);
      this.toastr.success('Login efetuado com sucesso!');
      this.router.navigate(['']);
    } catch {
      this.toastr.error('Erro ao processar o login. Tente novamente.');
    }
  }
}

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { LocalityPreferenceService } from '../../../services/local/locality-preference.service';
import { ToastrService } from 'ngx-toastr';
import { PasswordRevealDirective } from '../../../shared/password-reveal.directive';

type LoginStep = 'credentials' | 'mfa-select' | 'mfa-verify';
type MfaMethod = 'APP' | 'EMAIL';

@Component({
  selector: 'app-sign-in',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective],
  templateUrl: './sign-in.component.html',
  styleUrls: ['../auth-shell.css', './sign-in.component.css']
})
export class SignInComponent implements OnInit, OnDestroy {
  step: LoginStep = 'credentials';

  // Step 1 — credenciais
  email    = new FormControl('', [Validators.required, Validators.email]);
  password = new FormControl('', [Validators.required]);
  isLoginIncorrect = false;
  loading = false;

  // Step 2/3 — MFA
  totpCode  = new FormControl('', [Validators.required, Validators.pattern(/^\d{6}$/)]);
  mfaToken  = '';
  mfaError  = false;

  // Métodos de MFA disponíveis e escolhido
  mfaAppAvailable   = false;
  mfaEmailAvailable = false;
  mfaMethod: MfaMethod = 'APP';

  // Reenvio de código por e-mail (habilita após 15s)
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

  /**
   * Ao abrir a tela de entrada, o próprio navegador pede a localização — no
   * celular e no computador. Concedida, o município encontrado passa a reger as
   * telas; recusada, a navegação segue global, sem travar nada.
   */
  private async askLocation() {
    if (await this.locality.deniedBefore()) return;   // já recusada: não insiste
    this.locality.detect();
  }

  /** RF08/RF11: entra sem conta (leitura + registro de ocorrência anônima). */
  enterAnonymous() {
    this.auth.enterAnonymous();
    this.router.navigate(['']);
  }

  ngOnDestroy() { this.clearResendTimer(); }

  // ── STEP 1: Credenciais ──────────────────────────────────────────────────

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

          // Dois métodos: o usuário escolhe; um só: vai direto
          if (this.mfaAppAvailable && this.mfaEmailAvailable) {
            this.step = 'mfa-select';
          } else if (this.mfaEmailAvailable) {
            // Único método é e-mail: o back-end já enviou o código
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
      error: () => {
        this.loading = false;
        this.isLoginIncorrect = true;
        this.toastr.error('Email e/ou senha incorretos.');
      }
    });
  }

  // ── Seleção de método (quando há app E e-mail) ───────────────────────────

  chooseMethod(method: MfaMethod) {
    if (this.loading) return;   // o envio já está em curso: um 2º clique invalidaria o código
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

  // ── STEP 2: Verificar código (app ou e-mail) ─────────────────────────────

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

  // ── Reenvio do código por e-mail ─────────────────────────────────────────

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

  // ── Voltar para a tela de login ──────────────────────────────────────────

  backToLogin() {
    this.clearResendTimer();
    this.step = 'credentials';
    this.mfaError = false;
    this.totpCode.reset();
    this.password.reset();
    this.resendCountdown = 0;
  }

  // ── Finalizar login ──────────────────────────────────────────────────────

  private finishLogin(token: string) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // O id vem do token: sem ele a tela Meu Perfil não carrega nem salva nada.
      this.auth.saveSession(token, payload.email, payload.fullname, payload.role,
                            payload.id != null ? String(payload.id) : undefined);
      this.toastr.success('Login efetuado com sucesso!');
      this.router.navigate(['']);
    } catch {
      this.toastr.error('Erro ao processar o login. Tente novamente.');
    }
  }
}

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';

type LoginStep = 'credentials' | 'mfa-select' | 'mfa-verify' | 'mfa-setup' | 'mfa-confirm';
type MfaMethod = 'APP' | 'EMAIL';

@Component({
  selector: 'app-sign-in',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './sign-in.component.html',
  styleUrl: './sign-in.component.css'
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

  // Setup QR Code
  qrCodeUri    = '';
  secret       = '';
  qrCodeImgUrl = '';

  constructor(
    private router: Router,
    private auth: AuthenticationService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    if (this.auth.isAuthenticated()) this.router.navigate(['']);
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

        if (res.requiresMfaSetup) { this.loadMfaSetup(); return; }

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
    this.mfaMethod = method;
    this.mfaError = false;
    this.totpCode.reset();

    if (method === 'EMAIL') {
      this.auth.sendEmailCode(this.mfaToken).subscribe({
        next: () => {
          this.toastr.info('Enviamos um código para o seu e-mail.');
          this.step = 'mfa-verify';
          this.startResendCountdown();
        },
        error: () => this.toastr.error('Não foi possível enviar o código por e-mail.')
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

  // ── STEP setup: Carregar QR Code ─────────────────────────────────────────

  private loadMfaSetup() {
    this.auth.setupMfa(this.mfaToken).subscribe({
      next: (res: any) => {
        this.qrCodeUri   = res.qrCodeUri;
        this.secret      = res.secret;
        this.mfaToken    = res.mfaToken;
        this.qrCodeImgUrl =
          'https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=' +
          encodeURIComponent(res.qrCodeUri);
        this.step = 'mfa-setup';
      },
      error: () => this.toastr.error('Erro ao carregar configuração 2FA. Tente novamente.')
    });
  }

  continueToConfirm() {
    this.step = 'mfa-confirm';
    this.totpCode.reset();
  }

  confirmSetup() {
    if (this.totpCode.invalid) return;
    this.mfaError = false;
    this.loading  = true;
    this.auth.confirmMfaSetup(this.mfaToken, this.totpCode.value!).subscribe({
      next: (res: any) => { this.loading = false; this.finishLogin(res.token); },
      error: () => {
        this.loading  = false;
        this.mfaError = true;
        this.toastr.error('Código inválido. Verifique o app autenticador.');
      }
    });
  }

  // ── Finalizar login ──────────────────────────────────────────────────────

  private finishLogin(token: string) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.auth.saveSession(token, payload.email, payload.fullname, payload.role);
      this.toastr.success('Login efetuado com sucesso!');
      this.router.navigate(['']);
    } catch {
      this.toastr.error('Erro ao processar o login. Tente novamente.');
    }
  }
}

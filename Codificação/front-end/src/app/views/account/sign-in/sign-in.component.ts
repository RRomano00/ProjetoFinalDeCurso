import { ClipboardCodeDirective } from '../../../shared/clipboard-code.directive';
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
type MfaMethod = 'APP' | 'EMAIL' | 'SMS';

@Component({
  selector: 'app-sign-in',
  imports: [RouterModule, CommonModule, FormsModule, ReactiveFormsModule, PasswordRevealDirective, ClipboardCodeDirective,
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
  mfaSmsAvailable = false;
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
    if (this.email.invalid || this.password.invalid) {
      // Mostra os avisos dos campos em vez de o botão não fazer nada.
      this.email.markAsTouched();
      this.password.markAsTouched();
      return;
    }
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
          this.mfaSmsAvailable   = !!res.mfaSmsAvailable;

          const available = (['APP', 'EMAIL', 'SMS'] as MfaMethod[]).filter(m => this.isAvailable(m));
          if (available.length > 1) {
            this.step = 'mfa-select';
          } else if (available[0] === 'SMS') {
            // O SMS é pedido pela tela: se o celular gateway falhar, o erro aparece
            // aqui e o botão SMS fica à mão para tentar de novo.
            this.step = 'mfa-select';
            this.chooseMethod('SMS');
          } else {
            // Com um método só, o backend já enviou o e-mail junto da resposta do login.
            this.mfaMethod = available[0] ?? 'APP';
            this.totpCode.reset();
            this.step = 'mfa-verify';
            if (this.mfaMethod !== 'APP') this.startResendCountdown();
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

  isAvailable(method: MfaMethod): boolean {
    return method === 'APP' ? this.mfaAppAvailable
         : method === 'EMAIL' ? this.mfaEmailAvailable
         : this.mfaSmsAvailable;
  }

  get channelText(): string {
    return this.mfaMethod === 'SMS' ? 'por SMS para o seu celular' : 'para o seu e-mail';
  }

  private requestCode() {
    return this.mfaMethod === 'SMS'
      ? this.auth.sendSmsCode(this.mfaToken)
      : this.auth.sendEmailCode(this.mfaToken);
  }

  // 429: pediu de novo em menos de um minuto — o código anterior continua valendo.
  private codeErrorMessage(err: any): string {
    if (err?.status === 429) return 'Aguarde um minuto para pedir outro código.';
    if (err?.status === 502) return 'Não foi possível enviar o SMS agora. Tente de novo ou escolha outro método.';
    if (err?.status === 503) return 'O envio por SMS está indisponível. Escolha outro método.';
    return 'Não foi possível enviar o código.';
  }

  chooseMethod(method: MfaMethod) {
    if (this.loading) return;
    this.mfaMethod = method;
    this.mfaError = false;
    this.totpCode.reset();

    if (method === 'APP') { this.step = 'mfa-verify'; return; }

    this.loading = true;
    this.requestCode().subscribe({
      next: () => {
        this.loading = false;
        this.toastr.info(`Enviamos um código ${this.channelText}.`);
        this.step = 'mfa-verify';
        this.startResendCountdown();
      },
      error: (err: any) => {
        this.loading = false;
        if (err?.status === 429) { this.step = 'mfa-verify'; this.startResendCountdown(); }
        this.toastr.error(this.codeErrorMessage(err));
      }
    });
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
    this.requestCode().subscribe({
      next: () => {
        this.toastr.success(`Novo código enviado ${this.channelText}.`);
        this.startResendCountdown();
      },
      // 401: a etapa venceu (5 min, erros demais ou backend reiniciado) e só
      // um novo login gera outra.
      error: (err: any) => {
        if (err?.status !== 401) { this.toastr.error(this.codeErrorMessage(err)); return; }
        this.toastr.warning('A verificação expirou. Entre novamente.');
        this.backToLogin();
      }
    });
  }

  private startResendCountdown() {
    this.clearResendTimer();
    // O backend só aceita novo SMS depois de 60 s (proteção do chip contra o antispam da operadora).
    this.resendCountdown = this.mfaMethod === 'SMS' ? 60 : 15;
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

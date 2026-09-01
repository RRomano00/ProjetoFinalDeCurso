import { Component } from '@angular/core';
import { Router, RouterModule, RouterOutlet } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { User } from '../../../domain/model/user';
import { UserRole } from '../../../domain/model/user-role';
import { UpdatePasswordDto, UpdateProfileDto } from '../../../domain/dto/user-update-dto';
import { UserUpdateService } from '../../../services/user/user-update.service';
import { UserReadService } from '../../../services/user/user-read.service';
import { MfaService } from '../../../services/security/mfa.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-my-profile',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './my-profile.component.html',
  styleUrl: './my-profile.component.css'
})
export class MyProfileComponent {
  user: User = { fullname: '', email: '', password: '', role: UserRole.CITIZEN };
  form!: FormGroup;
  /** RF04: formulário de edição dos dados de perfil. */
  profileForm!: FormGroup;
  savingProfile = false;
  id? = localStorage.getItem('id');
  /** RNF19: mínimo 8 caracteres com letra, número e caractere especial. */
  passwordMinLength = 8;

  // ── 2FA / MFA ──
  mfaAppActive = false;
  mfaEmailActive = false;
  userRole = '';
  loadingMfa = true;
  // Fluxo de ativação do app autenticador
  showQr = false;
  qrCodeImgUrl = '';
  secret = '';
  confirmCode = '';
  savingMfa = false;
  // Ativação do e-mail (com código)
  enablingEmail = false;
  emailEnableCode = '';
  // Desativação com código
  disablingEmail = false;
  emailDisableCode = '';
  disablingApp = false;
  appDisableCode = '';
  // Exclusão de conta (RF06)
  deletingAccount = false;
  deleteCode = '';
  deletingNow = false;

  /** Precisa de código MFA para excluir a conta (algum método ativo). */
  get deleteNeedsCode(): boolean { return this.mfaAppActive || this.mfaEmailActive; }
  /** Quando o único método é e-mail, enviamos um código por e-mail. */
  get deleteUsesEmailCode(): boolean { return this.mfaEmailActive && !this.mfaAppActive; }

  /** Admin/Funcionário precisam manter ao menos um método de MFA ativo. */
  get isStaff(): boolean {
    return this.userRole === 'ADMINISTRATOR' || this.userRole === 'EMPLOYEE';
  }
  get activeMfaCount(): number {
    return (this.mfaEmailActive ? 1 : 0) + (this.mfaAppActive ? 1 : 0);
  }
  private blocksLastMfaRemoval(): boolean {
    if (this.isStaff && this.activeMfaCount <= 1) {
      this.toastr.error('Administrador/Funcionário precisa manter ao menos um método de verificação ativo.');
      return true;
    }
    return false;
  }

  constructor(
    private formBuilder: FormBuilder,
    private userUpdateService: UserUpdateService,
    private userReadService: UserReadService,
    private mfaService: MfaService,
    private toastr: ToastrService,
    private router: Router
  ) {
    this.initializeForm();
  }

  ngOnInit(): void {
    this.user.fullname = localStorage.getItem('fullname') || '';
    this.user.email = localStorage.getItem('email') || '';
    this.loadMfaStatus();
    this.loadProfile();
  }

  /** RF04: pré-preenche o formulário com os dados atuais do usuário. */
  async loadProfile() {
    if (!this.id) return;
    try {
      const data: any = await this.userReadService.findById(this.id);
      this.profileForm.patchValue({
        fullname:     data.fullname     || '',
        phoneNumber:  data.phoneNumber  || '',
        cep:          data.cep          || '',
        street:       data.street       || '',
        number:       data.number       || '',
        neighborhood: data.neighborhood || '',
        city:         data.city         || ''
      });
    } catch {
      this.toastr.error('Não foi possível carregar seus dados.');
    }
  }

  /** RF04: salva os dados de perfil (PUT /api/user/{id}). */
  saveProfile() {
    if (this.profileForm.invalid || !this.id) return;
    const dto: UpdateProfileDto = { id: +this.id, ...this.profileForm.value };
    this.savingProfile = true;
    this.userUpdateService.updateProfile(dto).subscribe({
      next: () => {
        localStorage.setItem('fullname', dto.fullname);
        this.user.fullname = dto.fullname;
        this.toastr.success('Dados atualizados com sucesso!');
        this.savingProfile = false;
      },
      error: () => {
        this.toastr.error('Não foi possível salvar. Tente novamente.');
        this.savingProfile = false;
      }
    });
  }

  async loadMfaStatus() {
    this.loadingMfa = true;
    try {
      const s = await this.mfaService.status();
      this.mfaAppActive = !!s.appActive;
      this.mfaEmailActive = !!s.emailActive;
      this.userRole = s.role || '';
    } catch {
      // silencioso: apenas não mostra o estado
    } finally {
      this.loadingMfa = false;
    }
  }

  async startAppSetup() {
    try {
      const res = await this.mfaService.setup();
      this.secret = res.secret;
      this.qrCodeImgUrl =
        'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' +
        encodeURIComponent(res.qrCodeUri);
      this.confirmCode = '';
      this.showQr = true;
    } catch {
      this.toastr.error('Não foi possível iniciar a configuração do aplicativo.');
    }
  }

  async confirmAppSetup() {
    if (!/^\d{6}$/.test(this.confirmCode)) {
      this.toastr.warning('Digite o código de 6 dígitos do aplicativo.');
      return;
    }
    this.savingMfa = true;
    try {
      await this.mfaService.confirm(this.confirmCode);
      this.toastr.success('Aplicativo autenticador ativado!');
      this.showQr = false;
      await this.loadMfaStatus();
    } catch {
      this.toastr.error('Código inválido. Verifique o aplicativo.');
    } finally {
      this.savingMfa = false;
    }
  }

  cancelAppSetup() {
    this.showQr = false;
    this.confirmCode = '';
  }

  // ── Ativar e-mail (com código enviado ao e-mail) ──
  async startEnableEmail() {
    this.enablingEmail = true;
    this.emailEnableCode = '';
    this.savingMfa = true;
    try {
      await this.mfaService.sendEmailEnableCode();
      this.toastr.info('Enviamos um código para o seu e-mail.');
    } catch {
      this.toastr.error('Não foi possível enviar o código.');
      this.enablingEmail = false;
    } finally {
      this.savingMfa = false;
    }
  }
  cancelEnableEmail() { this.enablingEmail = false; this.emailEnableCode = ''; }

  async confirmEnableEmail() {
    if (!/^\d{6}$/.test(this.emailEnableCode)) {
      this.toastr.warning('Digite o código de 6 dígitos enviado ao e-mail.');
      return;
    }
    this.savingMfa = true;
    try {
      await this.mfaService.enableEmail(this.emailEnableCode);
      this.toastr.success('MFA por e-mail ativado.');
      this.enablingEmail = false;
      await this.loadMfaStatus();
    } catch (err: any) {
      if (err?.status === 401) this.toastr.error('Código inválido ou expirado.');
      else this.toastr.error('Não foi possível ativar o MFA por e-mail.');
    } finally {
      this.savingMfa = false;
    }
  }

  // ── Desativar e-mail (com código enviado ao e-mail) ──
  async startDisableEmail() {
    if (this.blocksLastMfaRemoval()) return;
    this.disablingEmail = true;
    this.emailDisableCode = '';
    this.savingMfa = true;
    try {
      await this.mfaService.sendEmailDisableCode();
      this.toastr.info('Enviamos um código para o seu e-mail.');
    } catch {
      this.toastr.error('Não foi possível enviar o código.');
      this.disablingEmail = false;
    } finally {
      this.savingMfa = false;
    }
  }
  cancelDisableEmail() { this.disablingEmail = false; this.emailDisableCode = ''; }

  async confirmDisableEmail() {
    if (!/^\d{6}$/.test(this.emailDisableCode)) {
      this.toastr.warning('Digite o código de 6 dígitos enviado ao e-mail.');
      return;
    }
    this.savingMfa = true;
    try {
      await this.mfaService.disableEmail(this.emailDisableCode);
      this.toastr.success('MFA por e-mail desativado.');
      this.disablingEmail = false;
      await this.loadMfaStatus();
    } catch (err: any) {
      this.handleDisableError(err);
    } finally {
      this.savingMfa = false;
    }
  }

  // ── Desativar app (com código TOTP) ──
  startDisableApp() {
    if (this.blocksLastMfaRemoval()) return;
    this.disablingApp = true;
    this.appDisableCode = '';
  }
  cancelDisableApp() { this.disablingApp = false; this.appDisableCode = ''; }

  async confirmDisableApp() {
    if (!/^\d{6}$/.test(this.appDisableCode)) {
      this.toastr.warning('Digite o código de 6 dígitos do aplicativo.');
      return;
    }
    this.savingMfa = true;
    try {
      await this.mfaService.disableApp(this.appDisableCode);
      this.toastr.success('Aplicativo autenticador desativado.');
      this.disablingApp = false;
      await this.loadMfaStatus();
    } catch (err: any) {
      this.handleDisableError(err);
    } finally {
      this.savingMfa = false;
    }
  }

  private handleDisableError(err: any) {
    if (err?.status === 409) {
      this.toastr.error('Administrador/Funcionário precisa manter ao menos um método de verificação ativo.');
    } else if (err?.status === 401) {
      this.toastr.error('Código inválido ou expirado.');
    } else {
      this.toastr.error('Não foi possível desativar. Tente novamente.');
    }
  }

  // ── Excluir a própria conta (RF06) ──
  async startDeleteAccount() {
    this.deletingAccount = true;
    this.deleteCode = '';
    // Se o único método ativo é o e-mail, enviamos o código automaticamente.
    if (this.deleteUsesEmailCode) {
      this.deletingNow = true;
      try {
        await this.mfaService.sendAccountDeletionCode();
        this.toastr.info('Enviamos um código para o seu e-mail.');
      } catch {
        this.toastr.error('Não foi possível enviar o código.');
        this.deletingAccount = false;
      } finally {
        this.deletingNow = false;
      }
    }
  }
  cancelDeleteAccount() { this.deletingAccount = false; this.deleteCode = ''; }

  async confirmDeleteAccount() {
    if (this.deleteNeedsCode && !/^\d{6}$/.test(this.deleteCode)) {
      this.toastr.warning('Digite o código de 6 dígitos para confirmar a exclusão.');
      return;
    }
    this.deletingNow = true;
    try {
      await this.mfaService.deleteAccount(this.deleteCode);
      this.toastr.success('Sua conta foi excluída.');
      localStorage.clear();
      this.router.navigate(['/account/sign-in']);
    } catch (err: any) {
      if (err?.status === 401) this.toastr.error('Código inválido ou expirado.');
      else this.toastr.error('Não foi possível excluir a conta. Tente novamente.');
    } finally {
      this.deletingNow = false;
    }
  }

  initializeForm() {
    this.form = this.formBuilder.group({
      oldPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(this.passwordMinLength),
                         Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])/)]],
      confirmPassword: ['', [Validators.required]]
    });
    // RF04: dados de perfil (município obrigatório — RN01)
    this.profileForm = this.formBuilder.group({
      fullname:     ['', [Validators.required, Validators.minLength(3)]],
      phoneNumber:  [''],
      cep:          [''],
      street:       [''],
      number:       [''],
      neighborhood: [''],
      city:         ['', [Validators.required]]
    });
  }

  validatePasswords(): boolean {
    return this.form.controls['newPassword'].value === this.form.controls['confirmPassword'].value;
  }

  save() {
    if (!this.validatePasswords()) {
      this.toastr.error('As senhas não coincidem!');
      return;
    }
    const dto: UpdatePasswordDto = {
      id: this.id || '',
      oldPassword: this.form.controls['oldPassword'].value,
      newPassword: this.form.controls['newPassword'].value
    };
    this.userUpdateService.updatePassword(dto).subscribe({
      next: () => {
        this.toastr.success('Senha atualizada com sucesso!');
        this.form.reset();
        this.router.navigate(['']);
      },
      error: () => {
        this.toastr.error('Erro ao atualizar senha. Verifique a senha atual.');
      }
    });
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MfaService {
  private base = `${environment.api_endpoint}/mfa`;

  constructor(private http: HttpClient) {}

  /** Estado atual do 2FA do usuário: { appActive, emailActive, role } */
  status(): Promise<any> {
    return firstValueFrom(this.http.get<any>(`${this.base}/status`));
  }

  /** Gera o QR Code para configurar o app autenticador */
  setup(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/setup`, {}));
  }

  /** Confirma o código do app para ativar o MFA por aplicativo */
  confirm(totpCode: string): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/confirm`, { totpCode }));
  }

  /** Envia um código ao e-mail para confirmar a ATIVAÇÃO do MFA por e-mail */
  sendEmailEnableCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email/send-enable-code`, {}));
  }

  /** Ativa o MFA por e-mail — exige o código enviado ao e-mail */
  enableEmail(code: string): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email`, { totpCode: code }));
  }

  /** Envia um código ao e-mail para confirmar a desativação do MFA por e-mail */
  sendEmailDisableCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email/send-code`, {}));
  }

  /** Desativa o MFA por e-mail — exige o código enviado ao e-mail */
  disableEmail(code: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${this.base}/email`, { body: { totpCode: code } }));
  }

  /** Desativa o MFA por aplicativo — exige o código TOTP do app */
  disableApp(totpCode: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${this.base}`, { body: { totpCode } }));
  }

  /** Envia um código ao e-mail para confirmar a EXCLUSÃO da conta (quando o MFA por e-mail está ativo) */
  sendAccountDeletionCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${environment.api_endpoint}/user/account/delete-code`, {}));
  }

  /** Exclui a própria conta — exige o código MFA do método ativo (TOTP ou e-mail) */
  deleteAccount(code: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${environment.api_endpoint}/user/account`, { body: { totpCode: code } }));
  }
}

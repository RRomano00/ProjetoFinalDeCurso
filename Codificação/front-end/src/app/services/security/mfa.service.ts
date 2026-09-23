import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MfaService {
  private base = `${environment.api_endpoint}/mfa`;

  constructor(private http: HttpClient) {}

  status(): Promise<any> {
    return firstValueFrom(this.http.get<any>(`${this.base}/status`));
  }

  setup(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/setup`, {}));
  }

  confirm(totpCode: string): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/confirm`, { totpCode }));
  }

  sendEmailEnableCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email/send-enable-code`, {}));
  }

  enableEmail(code: string): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email`, { totpCode: code }));
  }

  sendEmailDisableCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${this.base}/email/send-code`, {}));
  }

  disableEmail(code: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${this.base}/email`, { body: { totpCode: code } }));
  }

  disableApp(totpCode: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${this.base}`, { body: { totpCode } }));
  }

  sendAccountDeletionCode(): Promise<any> {
    return firstValueFrom(this.http.post<any>(`${environment.api_endpoint}/user/account/delete-code`, {}));
  }

  deleteAccount(code: string): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${environment.api_endpoint}/user/account`, { body: { totpCode: code } }));
  }
}

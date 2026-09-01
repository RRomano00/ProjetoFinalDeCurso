import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UserCreateService {

  constructor(private http: HttpClient) {}

  /** Cadastro público de CITIZEN — POST /api/user/register */
  registerCitizen(data: {
    fullname: string; email: string; password: string;
    dateOfBirth: string; city: string; acceptsTerms: boolean;
    mfaEmailEnabled: boolean;
  }): Observable<any> {
    return this.http.post(`${environment.api_endpoint}/user/register`, data);
  }

  /** Criação de EMPLOYEE ou ADMINISTRATOR pelo admin — POST /api/user/employee */
  createStaff(data: { fullname: string; email: string; password: string; city: string; role: string }): Observable<any> {
    return this.http.post(`${environment.api_endpoint}/user/employee`, data);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UserCreateService {

  constructor(private http: HttpClient) {}

  registerCitizen(data: {
    fullname: string; email: string; password: string;
    dateOfBirth: string; city: string; acceptsTerms: boolean;
    mfaEmailEnabled: boolean;
    state?: string;
    phoneNumber?: string;
  }): Observable<any> {
    return this.http.post(`${environment.api_endpoint}/user/register`, data);
  }

  createStaff(data: {
    fullname: string; email: string; password: string;
    city: string; role: string; state?: string; phoneNumber?: string;
  }): Observable<any> {
    return this.http.post(`${environment.api_endpoint}/user/employee`, data);
  }
}

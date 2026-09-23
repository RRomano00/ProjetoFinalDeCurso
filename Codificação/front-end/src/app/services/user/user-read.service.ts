import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User } from '../../domain/model/user';

@Injectable({
  providedIn: 'root'
})
export class UserReadService {

  constructor(private http: HttpClient) { }

  findById(id: string): Promise<User> {
    return firstValueFrom(this.http.get<any>(`${environment.api_endpoint}/user/${id}`));
  }

  findAll(): Promise<any[]> {
    return firstValueFrom(this.http.get<any[]>(`${environment.api_endpoint}/user`));
  }

  setActive(id: number, active: boolean): Promise<any> {
    return firstValueFrom(this.http.put<any>(`${environment.api_endpoint}/user/${id}/active`, { active }));
  }

  setRole(id: number, role: string): Promise<any> {
    return firstValueFrom(this.http.put<any>(`${environment.api_endpoint}/user/${id}/role`, { role }));
  }

  delete(id: number): Promise<any> {
    return firstValueFrom(this.http.delete<any>(`${environment.api_endpoint}/user/${id}`));
  }
}

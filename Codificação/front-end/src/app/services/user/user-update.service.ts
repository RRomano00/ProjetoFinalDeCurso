import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UpdatePasswordDto, UpdateProfileDto } from '../../domain/dto/user-update-dto';

@Injectable({ providedIn: 'root' })
export class UserUpdateService {

  constructor(private http: HttpClient) { }

  /** Altera a senha do usuário (exige a senha atual). */
  updatePassword(data: UpdatePasswordDto): Observable<UpdatePasswordDto> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const body = {
      id: data.id,
      oldPassword: data.oldPassword,
      newPassword: data.newPassword
    };
    return this.http.put<UpdatePasswordDto>(
      `${environment.authentication_api_endpoint}/user/update-password`, body, { headers });
  }

  /** RF04: atualiza os dados de perfil do usuário (PUT /api/user/{id}). */
  updateProfile(data: UpdateProfileDto): Observable<void> {
    return this.http.put<void>(`${environment.api_endpoint}/user/${data.id}`, data);
  }
}

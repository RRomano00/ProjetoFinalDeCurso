import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Department } from '../domain/model/department';

/** RF22: departamentos da prefeitura — listagem e cadastro (equipe). */
@Injectable({ providedIn: 'root' })
export class DepartmentService {

  constructor(private http: HttpClient) {}

  /**
   * Sem município, devolve os setores do município de quem consulta (o
   * administrador vê todos). Com município, devolve os daquele município — é
   * assim que o leque de encaminhamento pede os setores do endereço da
   * ocorrência, que pode ser outro município.
   */
  findAll(city?: string, state?: string): Promise<Department[]> {
    const query = city && state
      ? `?city=${encodeURIComponent(city)}&state=${encodeURIComponent(state)}`
      : '';
    return firstValueFrom(
      this.http.get<Department[]>(`${environment.api_endpoint}/department${query}`)
    );
  }

  /** O back-end devolve 409 quando o nome se repete no município ou o e-mail já existe. */
  create(name: string, email: string, city: string, state: string): Promise<Department> {
    return firstValueFrom(
      this.http.post<Department>(`${environment.api_endpoint}/department`, { name, email, city, state })
    );
  }

  /** RF22: corrige o setor — só Administrador, e só no seu município. */
  update(id: number, name: string, email: string, city: string, state: string): Promise<Department> {
    return firstValueFrom(
      this.http.put<Department>(`${environment.api_endpoint}/department/${id}`, { name, email, city, state })
    );
  }
}

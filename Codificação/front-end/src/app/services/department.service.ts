import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Department } from '../domain/model/department';

@Injectable({ providedIn: 'root' })
export class DepartmentService {

  constructor(private http: HttpClient) {}

  findAll(city?: string, state?: string): Promise<Department[]> {
    const query = city && state
      ? `?city=${encodeURIComponent(city)}&state=${encodeURIComponent(state)}`
      : '';
    return firstValueFrom(
      this.http.get<Department[]>(`${environment.api_endpoint}/department${query}`)
    );
  }

  create(name: string, email: string, city: string, state: string): Promise<Department> {
    return firstValueFrom(
      this.http.post<Department>(`${environment.api_endpoint}/department`, { name, email, city, state })
    );
  }

  update(id: number, name: string, email: string, city: string, state: string): Promise<Department> {
    return firstValueFrom(
      this.http.put<Department>(`${environment.api_endpoint}/department/${id}`, { name, email, city, state })
    );
  }
}

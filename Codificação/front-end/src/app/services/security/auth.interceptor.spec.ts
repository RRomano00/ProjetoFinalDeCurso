import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('token', 'x.eyJleHAiOjk5OTk5OTk5OTl9.y');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { localStorage.removeItem('token'); ctrl.verify(); });

  it('não manda o login do navegador para o link do departamento', () => {
    http.get('/api/department-access/occurrence').subscribe();
    const req = ctrl.expectOne('/api/department-access/occurrence');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('continua mandando nas outras rotas', () => {
    http.get('/api/occurrence/mine').subscribe();
    const req = ctrl.expectOne('/api/occurrence/mine');
    expect(req.request.headers.get('Authorization')).toContain('Bearer ');
    req.flush([]);
  });
});

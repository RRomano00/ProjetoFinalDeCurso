import { Component, OnInit, HostListener } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';
import { InstallService } from '../../../services/local/install.service';
import { SessionWatchService } from '../../../services/security/session-watch.service';

@Component({
  selector: 'app-main',
  imports: [RouterModule, CommonModule],
  templateUrl: './main.component.html',
  styleUrl: './main.component.css'
})
export class MainComponent implements OnInit {
  fullname = '';
  role     = '';
  sidebarCollapsed = false;   
  userMenuOpen = false;
  /** Some o convite sem esperar o localStorage voltar na próxima verificação. */
  conviteDispensado = false;

  get isAdmin()      { return this.auth.isAdmin(); }
  get isSuperAdmin() { return this.auth.isSuperAdmin(); }
  get isStaff()   { return this.auth.isStaff(); }
  get isCitizen() { return this.auth.isCitizen(); }
  get isAnonymous() { return this.auth.isAnonymous(); }

  get roleLabel(): string {
    const map: Record<string, string> = {
      'ADMINISTRATOR': 'Administrador',
      'EMPLOYEE': 'Funcionário',
      'CITIZEN': 'Cidadão',
      'ANONYMOUS': 'Visitante'
    };
    return map[this.role] || this.role;
  }

  constructor(public auth: AuthenticationService, private router: Router,
              public install: InstallService, private toastr: ToastrService,
              public session: SessionWatchService) {}

  async instalarApp() {
    this.conviteDispensado = true;          // some o convite enquanto o diálogo abre
    const instrucao = await this.install.instalar();
    if (instrucao) this.toastr.info(instrucao, 'Instalar o aplicativo', { timeOut: 9000 });
  }

  /**
   * Convite de instalação. O navegador não abre mais a faixa nativa sozinho —
   * quem quiser instalar precisa achar o item no menu dele. O diálogo do
   * navegador só abre a partir de um gesto do usuário, então o caminho é este:
   * a aplicação convida, a pessoa toca e aí o navegador assume.
   */
  get mostrarConvite(): boolean {
    return !this.conviteDispensado && this.install.convidar;
  }

  /** "Agora não": o convite não volta; o item do menu lateral continua lá. */
  dispensarConvite() {
    this.conviteDispensado = true;
    this.install.dispensar();
  }

  ngOnInit() {
    this.fullname = localStorage.getItem('fullname') || (this.isAnonymous ? 'Visitante' : 'Usuário');
    this.role     = this.auth.role() || (this.isAnonymous ? 'ANONYMOUS' : '');
    this.sidebarCollapsed = window.innerWidth <= 480;
    this.session.start();
  }

  /** Sessão derrubada por um login em outro aparelho: só resta entrar de novo. */
  entrarNovamente() {
    this.session.ended.set(false);
    this.router.navigate(['/account/sign-in']);
  }

  logout() {
    this.session.stop();
    this.auth.logout();
    this.router.navigate(['/account/sign-in']);
  }

  toggleSidebar()  { this.sidebarCollapsed = !this.sidebarCollapsed; }
  toggleUserMenu() { this.userMenuOpen = !this.userMenuOpen; }
  closeUserMenu()  { this.userMenuOpen = false; }

  onSidebarNavClick() {
    if (window.innerWidth <= 480) {
      this.sidebarCollapsed = true;
    }
  }

  // Fecha o menu do usuário ao clicar fora dele
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (this.userMenuOpen && !target.closest('.user-menu-wrapper')) {
      this.userMenuOpen = false;
    }
  }
}

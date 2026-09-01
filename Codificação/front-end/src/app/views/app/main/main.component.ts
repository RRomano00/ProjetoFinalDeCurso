import { Component, OnInit, HostListener } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../services/security/authentication.service';

@Component({
  selector: 'app-main',
  imports: [RouterModule, CommonModule],
  templateUrl: './main.component.html',
  styleUrl: './main.component.css'
})
export class MainComponent implements OnInit {
  fullname = '';
  role     = '';
  sidebarCollapsed = false;   // recolhe a sidebar mostrando só ícones
  userMenuOpen = false;

  get isAdmin()   { return this.role === 'ADMINISTRATOR'; }
  get isStaff()   { return this.role === 'EMPLOYEE' || this.role === 'ADMINISTRATOR'; }
  get isCitizen() { return this.role === 'CITIZEN'; }
  /** RF08/RF11: visitante sem conta (só leitura + ocorrência anônima). */
  get isAnonymous() { return localStorage.getItem('anonymous') === 'true' && !localStorage.getItem('token'); }

  get roleLabel(): string {
    const map: Record<string, string> = {
      'ADMINISTRATOR': 'Administrador',
      'EMPLOYEE': 'Funcionário',
      'CITIZEN': 'Cidadão',
      'ANONYMOUS': 'Visitante'
    };
    return map[this.role] || this.role;
  }

  constructor(private auth: AuthenticationService, private router: Router) {}

  ngOnInit() {
    this.fullname = localStorage.getItem('fullname') || (this.isAnonymous ? 'Visitante' : 'Usuário');
    this.role     = localStorage.getItem('role')     || (this.isAnonymous ? 'ANONYMOUS' : '');
    // No celular a sidebar começa recolhida (só ícones); no desktop, expandida.
    this.sidebarCollapsed = window.innerWidth <= 480;
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/account/sign-in']);
  }

  toggleSidebar()  { this.sidebarCollapsed = !this.sidebarCollapsed; }
  toggleUserMenu() { this.userMenuOpen = !this.userMenuOpen; }
  closeUserMenu()  { this.userMenuOpen = false; }

  // No celular, ao escolher um item do menu, fecha o drawer (recolhe a sidebar).
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

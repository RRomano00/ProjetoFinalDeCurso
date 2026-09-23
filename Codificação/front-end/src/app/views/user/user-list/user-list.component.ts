import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UserReadService } from '../../../services/user/user-read.service';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-user-list',
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.css'
})
export class UserListComponent implements OnInit {
  users: any[] = [];
  loading = true;
  togglingId: number | null = null;
  deletingId: number | null = null;
  pendingDelete: any = null;

  myId = Number(localStorage.getItem('id') || 0);

  search       = '';
  filterRole   = '';
  filterCity   = '';
  filterActive = '';

  readonly roleOptions = ['SUPER_ADMIN', 'ADMINISTRATOR', 'EMPLOYEE', 'CITIZEN'];

  get cityOptions(): string[] {
    return [...new Set(this.users.map(u => u.city).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  get filtered(): any[] {
    const q = this.search.trim().toLowerCase();
    return this.users.filter(u =>
      (!q || `${u.fullname} ${u.email}`.toLowerCase().includes(q))
      && (!this.filterRole   || u.role === this.filterRole)
      && (!this.filterCity   || u.city === this.filterCity)
      && (!this.filterActive || String(!!u.active) === this.filterActive));
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterRole || this.filterCity || this.filterActive);
  }

  clearFilters() {
    this.search = this.filterRole = this.filterCity = this.filterActive = '';
  }

  constructor(
    private userReadService: UserReadService,
    private auth: AuthenticationService,
    private toastr: ToastrService
  ) {
    this.assignableRoles = ['CITIZEN', 'EMPLOYEE', 'ADMINISTRATOR'];
    if (this.auth.isSuperAdmin()) this.assignableRoles.push('SUPER_ADMIN');
  }

  assignableRoles: string[] = [];

  roleDraft: Record<number, string> = {};
  savingRoleId: number | null = null;

  roleOf(user: any): string { return this.roleDraft[user.id] ?? user.role; }

  roleChanged(user: any): boolean { return this.roleOf(user) !== user.role; }

  async saveRole(user: any) {
    const role = this.roleOf(user);
    this.savingRoleId = user.id;
    try {
      await this.userReadService.setRole(user.id, role);
      this.toastr.success(`Perfil de ${user.fullname} alterado para ${this.roleLabel(role)}.`);
      delete this.roleDraft[user.id];
      await this.load();
    } catch (err: any) {
      this.toastr.error(err?.error?.error || 'Não foi possível alterar o perfil.');
    } finally {
      this.savingRoleId = null;
    }
  }

  async ngOnInit() {
    await this.load();
  }

  async load() {
    this.loading = true;
    try {
      this.users = await this.userReadService.findAll();
    } catch {
      this.toastr.error('Erro ao carregar usuários.');
    } finally {
      this.loading = false;
    }
  }

  roleLabel(role: string): string {
    const map: Record<string, string> = {
      'SUPER_ADMIN': 'Super Administrador', 'ADMINISTRATOR': 'Administrador',
      'EMPLOYEE': 'Funcionário', 'CITIZEN': 'Cidadão'
    };
    return map[role] || role;
  }

  async toggleActive(user: any) {
    this.togglingId = user.id;
    try {
      await this.userReadService.setActive(user.id, !user.active);
      user.active = !user.active;
      this.toastr.success(user.active ? 'Conta ativada.' : 'Conta inativada.');
    } catch {
      this.toastr.error('Não foi possível alterar o status da conta.');
    } finally {
      this.togglingId = null;
    }
  }

  askRemove(user: any) { this.pendingDelete = user; }

  async confirmRemove() {
    const user = this.pendingDelete;
    if (!user) return;
    this.deletingId = user.id;
    try {
      await this.userReadService.delete(user.id);
      this.users = this.users.filter(u => u.id !== user.id);
      this.toastr.success('Conta excluída.');
    } catch {
      this.toastr.error('Não foi possível excluir a conta.');
    } finally {
      this.deletingId = null;
      this.pendingDelete = null;
    }
  }
}

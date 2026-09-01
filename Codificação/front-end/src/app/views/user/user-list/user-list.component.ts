import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { UserReadService } from '../../../services/user/user-read.service';
import { ToastrService } from 'ngx-toastr';

/** RF15: painel do administrador para listar, ativar e inativar contas. */
@Component({
  selector: 'app-user-list',
  imports: [CommonModule, RouterModule],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.css'
})
export class UserListComponent implements OnInit {
  users: any[] = [];
  loading = true;
  togglingId: number | null = null;

  myId = Number(localStorage.getItem('id') || 0);

  constructor(
    private userReadService: UserReadService,
    private toastr: ToastrService
  ) {}

  async ngOnInit() {
    await this.load();
  }

  private async load() {
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
      'ADMINISTRATOR': 'Administrador', 'EMPLOYEE': 'Funcionário', 'CITIZEN': 'Cidadão'
    };
    return map[role] || role;
  }

  /** Ativa/inativa a conta (inativo não consegue mais entrar). */
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
}

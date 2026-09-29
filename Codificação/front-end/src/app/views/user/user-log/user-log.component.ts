import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserReadService } from '../../../services/user/user-read.service';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { ToastrService } from 'ngx-toastr';
import { persistFilters } from '../../../shared/persist-filters';

@Component({
  selector: 'app-user-log',
  imports: [CommonModule, FormsModule],
  templateUrl: './user-log.component.html',
  styleUrls: ['../user-list/user-list.component.css', '../../table-cards.css']
})
export class UserLogComponent implements OnInit {
  logs: any[] = [];
  loading = true;

  search       = '';
  filterAction = '';
  filterRole   = '';
  filterCity   = '';
  dateFrom     = '';
  dateTo       = '';

  readonly isSuperAdmin: boolean;
  readonly roleOptions = ['SUPER_ADMIN', 'ADMINISTRATOR', 'EMPLOYEE', 'CITIZEN'];

  constructor(
    private userReadService: UserReadService,
    auth: AuthenticationService,
    private toastr: ToastrService
  ) {
    this.isSuperAdmin = auth.isSuperAdmin();
    persistFilters(this, 'user-log',
      ['search', 'filterAction', 'filterRole', 'filterCity', 'dateFrom', 'dateTo']);
  }

  get cityOptions(): string[] {
    return [...new Set(this.logs.map(l => l.city).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  get filtered(): any[] {
    const q = this.search.trim().toLowerCase();
    return this.logs.filter(l => {
      const day = String(l.createdAt).slice(0, 10);
      return (!q || `${l.actorName} ${l.actorEmail} ${l.targetName} ${l.targetEmail} ${l.occurrenceProtocol} ${l.occurrenceTitle}`
                      .toLowerCase().includes(q))
        && (!this.filterAction || l.action === this.filterAction)
        && (!this.filterRole   || l.targetRole === this.filterRole)
        && (!this.filterCity   || l.city === this.filterCity)
        && (!this.dateFrom     || day >= this.dateFrom)
        && (!this.dateTo       || day <= this.dateTo);
    });
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterAction || this.filterRole
           || this.filterCity || this.dateFrom || this.dateTo);
  }

  clearFilters() {
    this.search = this.filterAction = this.filterRole = this.filterCity = this.dateFrom = this.dateTo = '';
  }

  async ngOnInit() {
    try {
      this.logs = await this.userReadService.findLogs();
    } catch {
      this.toastr.error('Erro ao carregar os logs.');
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
}

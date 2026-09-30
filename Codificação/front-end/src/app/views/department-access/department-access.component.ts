import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { DepartmentAccessService, DepartmentOccurrenceView } from '../../services/department-access.service';
import { OccurrenceHistory } from '../../domain/model/occurrence';
import { statusLabel, typeLabel } from '../../domain/occurrence-labels';
import { CopyProtocolComponent } from '../../shared/copy-protocol.component';

@Component({
  selector: 'app-department-access',
  imports: [CommonModule, FormsModule, CopyProtocolComponent],
  templateUrl: './department-access.component.html',
  styleUrl: './department-access.component.css'
})
export class DepartmentAccessComponent implements OnInit {
  private token = '';
  view?: DepartmentOccurrenceView;
  loading = true;
  errorMessage = '';

  requestOpen = false;
  file: File | null = null;
  preview = '';
  message = '';
  sending = false;

  statusLabel = statusLabel;
  typeLabel = typeLabel;

  constructor(private route: ActivatedRoute, private service: DepartmentAccessService,
              private toastr: ToastrService) {}

  ngOnInit() {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    this.load();
  }

  async load(id?: number) {
    this.loading = true;
    try {
      this.view = await this.service.view(this.token, id);
      this.errorMessage = '';
    } catch (err: any) {
      this.view = undefined;
      this.errorMessage = err?.error?.error || 'Não foi possível abrir a ocorrência.';
    } finally { this.loading = false; }
  }

  get mapsRoute(): string | null {
    const o = this.view?.occurrence;
    return o?.latitude != null && o?.longitude != null
      ? `https://www.google.com/maps/dir/?api=1&destination=${o.latitude},${o.longitude}` : null;
  }

  historyLabel(h: OccurrenceHistory): string {
    const reaberta = h.newStatus === 'EM_ANDAMENTO' && (h.oldStatus === 'CONCLUIDA' || h.oldStatus === 'INDEFERIDA');
    return statusLabel(h.newStatus) + (reaberta ? ' (Reaberta)' : '');
  }

  onFile(event: Event) {
    const f = (event.target as HTMLInputElement).files?.[0] || null;
    if (this.preview) URL.revokeObjectURL(this.preview);
    this.file = f;
    this.preview = f ? URL.createObjectURL(f) : '';
  }

  closeRequest() {
    if (this.sending) return;
    this.requestOpen = false;
    this.file = null;
    this.message = '';
    if (this.preview) URL.revokeObjectURL(this.preview);
    this.preview = '';
  }

  async sendRequest() {
    const id = this.view?.occurrence.id;
    if (!this.file || id == null || this.sending) return;
    this.sending = true;
    try {
      await this.service.requestCompletion(this.token, id, this.file, this.message);
      this.toastr.success('Solicitação enviada. A prefeitura vai conferir e concluir a ocorrência.');
      this.sending = false;
      this.closeRequest();
      await this.load(id);
    } catch (err: any) {
      this.toastr.error(err?.error?.error || 'Não foi possível enviar a solicitação.');
      this.sending = false;
    }
  }
}

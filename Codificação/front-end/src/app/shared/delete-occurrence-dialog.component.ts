import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { Occurrence } from '../domain/model/occurrence';
import { statusLabel, statusColor } from '../domain/occurrence-labels';
import { OccurrenceReadService } from '../services/occurrence-read.service';
import { OccurrenceEditService } from '../services/occurrence-edit.service';
import { CopyProtocolComponent } from './copy-protocol.component';

// Exclusão pelo Super Administrador. Com ocorrências agrupadas, deixa escolher quais da cadeia
// excluir juntas; o backend continua excluindo uma a uma.
@Component({
  selector: 'app-delete-occurrence-dialog',
  standalone: true,
  imports: [CommonModule, RouterModule, CopyProtocolComponent],
  template: `
    <div class="modal-overlay" (click)="deleting || closed.emit([])">
      <div class="modal-card" [class.modal-card--wide]="group.length > 1" role="alertdialog" aria-modal="true"
           aria-labelledby="excluir-ocorrencia-titulo" (click)="$event.stopPropagation()">
        <h2 id="excluir-ocorrencia-titulo">Excluir ocorrência</h2>

        <p *ngIf="loading">Carregando ocorrências agrupadas<span class="loading-dots" aria-hidden="true"></span></p>

        <ng-container *ngIf="!loading && group.length <= 1">
          <p>
            O status da ocorrência <strong>{{ occurrence.protocolNumber }}</strong>
            ({{ occurrence.title || 'sem título' }}) passa para <strong>Finalizada</strong>: só o Super Administrador
            continua a vê-la, as fotos são apagadas e o autor é notificado por e-mail.
          </p>
          <p><strong>Esta ação não pode ser desfeita.</strong></p>
        </ng-container>

        <ng-container *ngIf="!loading && group.length > 1">
          <p>
            Esta ocorrência está agrupada com {{ group.length === 2 ? 'outra' : 'outras ' + (group.length - 1) }}.
            Marque as que serão excluídas: o status delas passa para <strong>Finalizada</strong>, as fotos são
            apagadas e cada autor é notificado por e-mail.
          </p>
          <p><strong>Esta ação não pode ser desfeita.</strong></p>
          <label class="select-all">
            <input type="checkbox" [checked]="allSelected"
                   [indeterminate]="selected.size > 0 && !allSelected" (change)="toggleAll()" />
            {{ allSelected ? 'Desmarcar todas' : 'Selecionar todas' }}
          </label>
          <ul class="group-list">
            <li *ngFor="let g of group" [class.checked]="selected.has(g.id!)" (click)="toggle(g.id!)">
              <input type="checkbox" [checked]="selected.has(g.id!)"
                     [attr.aria-label]="'Excluir a ocorrência ' + g.protocolNumber"
                     (click)="$event.stopPropagation()" (change)="toggle(g.id!)" />
              <div class="row-body">
                <div class="row-head">
                  <a [routerLink]="['/occurrence/detail', g.id]" target="_blank" rel="noopener"
                     (click)="$event.stopPropagation()"
                     [attr.aria-label]="'Abrir a ocorrência ' + g.protocolNumber + ' em uma nova aba'">
                    {{ g.protocolNumber }}</a>
                  <app-copy-protocol [value]="g.protocolNumber" [small]="true" />
                  <span *ngIf="g.id === occurrence.id" class="current">(atual)</span>
                  <span class="status" [style.color]="statusColor(g.status)">{{ statusLabel(g.status) }}</span>
                </div>
                <span class="row-title">{{ g.title || 'sem título' }}</span>
                <span class="row-last">{{ lastMessage[g.id!] || 'Sem mensagens no histórico.' }}</span>
              </div>
            </li>
          </ul>
        </ng-container>

        <div class="modal-actions">
          <button type="button" class="btn-secondary" (click)="closed.emit([])" [disabled]="deleting">Cancelar</button>
          <button type="button" class="btn-danger" (click)="group.length > 1 ? confirming = true : confirm()"
                  [disabled]="loading || deleting || selected.size === 0">
            @if (deleting) {Excluindo<span class="loading-dots" aria-hidden="true"></span>} @else { {{ selected.size > 1 ? 'Excluir ' + selected.size + ' ocorrências' : 'Excluir ocorrência' }} }
          </button>
        </div>
      </div>
    </div>

    @if (confirming) {
      <div class="modal-overlay" (click)="deleting || (confirming = false)">
        <div class="modal-card" role="alertdialog" aria-modal="true" aria-labelledby="confirmar-exclusao-titulo"
             (click)="$event.stopPropagation()">
          <h2 id="confirmar-exclusao-titulo">Confirmar exclusão</h2>
          <p>
            {{ selected.size > 1 ? 'Serão excluídas ' + selected.size + ' ocorrências:' : 'Será excluída a ocorrência' }}
            <strong>{{ selectedProtocols }}</strong>.
          </p>
          <p><strong>Esta ação não pode ser desfeita.</strong></p>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" (click)="confirming = false" [disabled]="deleting">Voltar</button>
            <button type="button" class="btn-danger" (click)="confirm()" [disabled]="deleting">
              @if (deleting) {Excluindo<span class="loading-dots" aria-hidden="true"></span>} @else { {{ 'Sim, excluir' }} }
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .modal-card--wide { max-width: 640px; max-height: calc(100vh - 2rem); }
    .select-all { display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; font-weight: 600; cursor: pointer; }
    .group-list { list-style: none; margin: 0; padding: 0; min-height: 0; overflow-y: auto; border: 1px solid var(--line); border-radius: var(--r-ctl); }
    .group-list li { display: flex; gap: 0.625rem; align-items: flex-start; padding: 0.625rem 0.75rem;
                     border-bottom: 1px solid var(--line); cursor: pointer; }
    .group-list li:last-child { border-bottom: none; }
    .group-list li:hover { background: var(--surface-2); }
    .group-list li.checked { background: var(--st-refused-bg); }
    input[type=checkbox] { width: 16px; height: 16px; margin-top: 0.15rem; cursor: pointer; accent-color: var(--st-refused); flex: none; }
    .row-body { display: flex; flex-direction: column; gap: 0.125rem; min-width: 0; font-size: 0.8125rem; }
    .row-head { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: baseline; }
    .row-head a { font-family: var(--font-mono, monospace); font-weight: 600; color: var(--plate); }
    .current { color: var(--ink-faint); }
    .status { font-weight: 600; }
    .row-title { color: var(--ink); font-weight: 500; }
    .row-last { color: var(--ink-mute); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .btn-secondary, .btn-danger { padding: 0.5rem 1rem; border-radius: var(--r-ctl); font-size: 0.875rem; font-weight: 600; cursor: pointer; }
    .btn-secondary { border: 1px solid var(--line); background: var(--surface); color: var(--ink); }
    .btn-danger { border: 1px solid var(--st-refused); background: var(--st-refused); color: #fff; }
    .btn-danger:hover:not(:disabled) { filter: brightness(0.92); }
    button:disabled { opacity: 0.6; cursor: not-allowed; }
  `]
})
export class DeleteOccurrenceDialogComponent implements OnInit {
  @Input({ required: true }) occurrence!: Occurrence;
  /** Emite os ids efetivamente excluídos (vazio ao cancelar). */
  @Output() closed = new EventEmitter<number[]>();

  group: Occurrence[] = [];
  lastMessage: Record<number, string> = {};
  selected = new Set<number>();
  loading = true;
  deleting = false;
  confirming = false;

  statusLabel = statusLabel;
  statusColor = statusColor;

  constructor(
    private readService: OccurrenceReadService,
    private editService: OccurrenceEditService,
    private toastr: ToastrService
  ) {}

  async ngOnInit() {
    this.selected.add(this.occurrence.id!);
    try {
      this.group = (await this.readService.getGroup(this.occurrence.id!))
        .filter(g => g.id != null && g.status !== 'FINALIZADA');
    } catch { this.group = []; }
    // ponytail: um GET de histórico por ocorrência; grupos são pequenos (raio de 50 m).
    if (this.group.length > 1) {
      await Promise.all(this.group.map(async g => {
        try {
          const h = await this.readService.getHistory(g.id!);  // mais recente primeiro
          this.lastMessage[g.id!] = h.find(x => x.observation?.trim())?.observation || '';
        } catch { }
      }));
    }
    this.loading = false;
  }

  get selectedProtocols(): string {
    return this.group.filter(g => this.selected.has(g.id!)).map(g => g.protocolNumber).join(', ');
  }

  get allSelected(): boolean { return this.group.length > 0 && this.selected.size === this.group.length; }

  toggle(id: number) { if (!this.selected.delete(id)) this.selected.add(id); }

  toggleAll() {
    this.selected = this.allSelected ? new Set() : new Set(this.group.map(g => g.id!));
  }

  async confirm() {
    if (this.deleting || this.selected.size === 0) return;
    this.deleting = true;
    const done: number[] = [];
    let forbidden = false;
    for (const id of this.selected) {
      try { await this.editService.delete(id); done.push(id); }
      catch (err: any) {
        if (err?.status === 404) done.push(id);  // já estava finalizada
        if (err?.status === 403) { forbidden = true; break; }
      }
    }
    this.deleting = false;
    this.confirming = false;

    const failed = this.selected.size - done.length;
    if (forbidden) this.toastr.error('Apenas o Super Administrador pode excluir ocorrências.');
    else if (failed) this.toastr.error(`Não foi possível excluir ${failed} de ${this.selected.size} ocorrência(s).`);
    if (done.length === 1 && !failed) {
      this.toastr.success(`Ocorrência ${this.protocolOf(done[0])} excluída: agora ela está Finalizada.`);
    } else if (done.length) {
      this.toastr.success(`${done.length} ocorrências excluídas: agora estão Finalizadas.`);
    }
    if (done.length || !failed) this.closed.emit(done);
  }

  private protocolOf(id: number): string {
    return (this.group.find(g => g.id === id) || this.occurrence).protocolNumber || '';
  }
}

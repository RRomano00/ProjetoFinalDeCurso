import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DepartmentService } from '../../../services/department.service';
import { LocalityService, CityOptions } from '../../../services/local/locality.service';
import { UserReadService } from '../../../services/user/user-read.service';
import { AuthenticationService } from '../../../services/security/authentication.service';
import { Department } from '../../../domain/model/department';
import { ToastrService } from 'ngx-toastr';

/**
 * RF22: departamentos da prefeitura — os destinos possíveis do encaminhamento
 * de uma ocorrência.
 *
 * O formulário de cadastro abre na própria lista, e não em outra rota: a
 * validação aqui é "não repetir nome nem e-mail", então quem digita precisa ver
 * o que já existe.
 */
@Component({
  selector: 'app-department-list',
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './department-list.component.html',
  styleUrl: './department-list.component.css'
})
export class DepartmentListComponent implements OnInit {

  departments: Department[] = [];
  loading = true;
  saving = false;
  formOpen = false;
  /** null = cadastro; id = edição daquele setor. O formulário é o mesmo. */
  editingId: number | null = null;
  form!: FormGroup;

  @ViewChild('nameInput') nameInput?: ElementRef<HTMLInputElement>;

  // ── Filtros da listagem ──
  search     = '';
  filterCity = '';

  /** Municípios que existem na lista — não adianta oferecer o que não está aqui.
      (Nome distinto de `cityOptions`, que é a lista do IBGE usada no formulário.) */
  get filterCityOptions(): string[] {
    return [...new Set(this.departments.map(d => d.city).filter((c): c is string => !!c))]
      .sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  get filtered(): Department[] {
    const q = this.search.trim().toLowerCase();
    return this.departments.filter(d =>
      (!q || `${d.name} ${d.email}`.toLowerCase().includes(q))
      && (!this.filterCity || d.city === this.filterCity));
  }

  get hasFilters(): boolean { return !!(this.search || this.filterCity); }

  clearFilters() { this.search = this.filterCity = ''; }

  /** UF e municípios, no mesmo padrão dos cadastros de usuário e de ocorrência. */
  units: { uf: string; name: string }[] = [];
  cityOptions: CityOptions = { list: [], ready: false };

  constructor(
    private departmentService: DepartmentService,
    private fb: FormBuilder,
    private toastr: ToastrService,
    private locality: LocalityService,
    private userReadService: UserReadService,
    private auth: AuthenticationService
  ) {
    this.form = this.fb.group({
      name:  ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      // O setor é municipal: é o município do endereço da ocorrência que define
      // para onde ela pode ser encaminhada. Vem preenchido com o município de
      // quem cadastra, que é onde o funcionário pode cadastrar.
      state: ['', [Validators.required]],
      city:  ['', [Validators.required]]
    });
    this.units = this.locality.units;
    this.cityOptions = this.locality.bindCityToUf(this.form);
  }

  async ngOnInit() {
    await Promise.all([this.load(), this.loadMyCity()]);
  }

  /**
   * Município de quem está cadastrando: o servidor só aceita esse, para a
   * equipe — o setor é destino de encaminhamento da própria prefeitura. Por
   * isso o campo vem preenchido e bloqueado; só o Super Administrador, que não
   * tem município, escolhe.
   */
  private async loadMyCity() {
    if (this.canChooseCity) return;
    const id = localStorage.getItem('id');
    if (!id) return;
    try {
      const me: any = await this.userReadService.findById(id);
      if (me?.city) this.form.patchValue({ state: me.state || '', city: me.city });
    } catch { /* sem preenchimento prévio: a pessoa informa o município */ }
    this.form.get('state')!.disable();
    this.form.get('city')!.disable();
  }

  /** RF25: sem município próprio, o Super Administrador cadastra em qualquer um. */
  get canChooseCity() { return this.auth.isSuperAdmin(); }

  private async load() {
    this.loading = true;
    try {
      this.departments = await this.departmentService.findAll();
    } catch {
      this.toastr.error('Não foi possível carregar os departamentos.');
    } finally {
      this.loading = false;
    }
  }

  /** RF25: corrigir o setor é do administrador; o funcionário cadastra e consulta. */
  get canEdit() { return this.auth.isAdmin(); }

  openForm() {
    this.editingId = null;
    this.formOpen = true;
    setTimeout(() => this.nameInput?.nativeElement.focus(), 0);
  }

  /** Abre o mesmo formulário já preenchido com o setor escolhido. */
  edit(d: Department) {
    this.editingId = d.id;
    this.formOpen = true;
    this.form.patchValue({ name: d.name, email: d.email, state: d.state || '', city: d.city || '' });
    // Trocar a UF repovoa a lista do IBGE e pode limpar o município digitado;
    // o que está gravado é reposto depois disso.
    // ponytail: repõe no tique seguinte — basta para a lista em cache; se a UF
    // ainda não tiver sido buscada, esperar o Observable de municípios.
    setTimeout(() => {
      this.form.patchValue({ city: d.city || '' }, { emitEvent: false });
      this.nameInput?.nativeElement.focus();
    }, 0);
  }

  closeForm() {
    this.formOpen = false;
    this.editingId = null;
    // Mantém o município: quem cadastra vários setores é sempre do mesmo lugar.
    const { state, city } = this.form.getRawValue();
    this.form.reset({ state, city });
  }

  /**
   * A duplicidade é conferida aqui, contra a lista em tela, para responder sem
   * ida ao servidor; o servidor confere de novo e é ele quem tem a palavra
   * final, porque a lista local pode estar desatualizada.
   *
   * O nome se repete entre municípios — cada prefeitura tem a sua Secretaria de
   * Obras —, então só conflita dentro do mesmo município. O e-mail é o destino
   * do encaminhamento: não se repete em lugar nenhum.
   */
  private duplicateField(): 'name' | 'email' | null {
    // getRawValue, e não value: o município fica desabilitado para a equipe e
    // controle desabilitado não entra em form.value.
    const bruto = this.form.getRawValue();
    const name  = (bruto.name  || '').trim().toLowerCase();
    const email = (bruto.email || '').trim().toLowerCase();
    const city  = (bruto.city  || '').trim().toLowerCase();
    const state = (bruto.state || '').trim().toUpperCase();

    const mesmoMunicipio = (d: any) =>
      d.city?.trim().toLowerCase() === city && d.state?.trim().toUpperCase() === state;
    // Na edição, o próprio setor não conflita consigo mesmo.
    const outros = this.departments.filter(d => d.id !== this.editingId);

    if (outros.some(d => mesmoMunicipio(d) && d.name?.trim().toLowerCase() === name)) return 'name';
    if (outros.some(d => d.email?.trim().toLowerCase() === email)) return 'email';
    return null;
  }

  async save() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    const duplicate = this.duplicateField();
    if (duplicate === 'name') {
      this.toastr.warning('Já existe um departamento com este nome neste município.');
      return;
    }
    if (duplicate === 'email') {
      this.toastr.warning('Já existe um departamento com este e-mail.');
      return;
    }

    this.saving = true;
    const bruto = this.form.getRawValue();
    const dados: [string, string, string, string] = [
      bruto.name.trim(), bruto.email.trim(),
      bruto.city.trim(), this.locality.normalizeUf(bruto.state) ?? ''];
    try {
      const saved = this.editingId == null
        ? await this.departmentService.create(...dados)
        : await this.departmentService.update(this.editingId, ...dados);
      this.departments = [...this.departments.filter(d => d.id !== this.editingId), saved].sort((a, b) =>
        (a.city || '').localeCompare(b.city || '', 'pt-BR') || a.name.localeCompare(b.name, 'pt-BR'));
      this.toastr.success(`${saved.name} ${this.editingId == null ? 'cadastrado' : 'atualizado'}.`);
      this.closeForm();
    } catch (err: any) {
      if (err?.status === 409) {
        this.toastr.error(err.error?.error || 'Nome ou e-mail já cadastrados.');
        await this.load();          // a lista estava desatualizada
      } else if (err?.status === 400) {
        this.toastr.error(err.error?.error || 'Verifique os dados informados.');
      } else if (err?.status === 403) {
        this.toastr.error(err.error?.error || 'Você só pode editar departamentos do seu município.');
      } else {
        this.toastr.error(`Não foi possível ${this.editingId == null ? 'cadastrar' : 'editar'} o departamento.`);
      }
    } finally {
      this.saving = false;
    }
  }
}

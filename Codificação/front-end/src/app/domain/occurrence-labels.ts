/**
 * Fonte única de labels e cores de ocorrência (categoria, status e prioridade).
 * Evita duplicar os mesmos mapas em cada componente.
 */

/** Categorias disponíveis (valor do enum do back-end + label exibido). */
export const OCCURRENCE_TYPES: { value: string; label: string }[] = [
  { value: 'BURACO_NA_RUA_OU_CALCADA',              label: 'Buraco na Rua ou Calçada' },
  { value: 'POSTE_COM_LUZ_QUEIMADA',                label: 'Poste com Luz Queimada' },
  { value: 'LIXO_ACUMULADO_OU_TERRENO_SUJO',        label: 'Lixo Acumulado ou Terreno Sujo' },
  { value: 'SINALIZACAO_OU_SEMAFORO_COM_DEFEITO',   label: 'Sinalização ou Semáforo com Defeito' },
  { value: 'PROBLEMAS_EM_PRACAS_E_PARQUES',         label: 'Problemas em Praças e Parques' },
  { value: 'FALHAS_NO_TRANSPORTE_PUBLICO',          label: 'Falhas no Transporte Público' },
  { value: 'PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA', label: 'Problemas em Posto de Saúde ou Escola' },
  { value: 'SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO',    label: 'Som Alto ou Perturbação do Sossego' },
  { value: 'OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO',   label: 'Obra Irregular ou Imóvel Abandonado' },
  { value: 'MAUS_TRATOS_AOS_ANIMAIS',               label: 'Maus Tratos aos Animais' },
  { value: 'PESSOA_PRECISANDO_DE_AJUDA',            label: 'Pessoa Precisando de Ajuda' },
  { value: 'OUTROS_PROBLEMAS',                      label: 'Outros Problemas' },
];

/** Cor de destaque de cada categoria (badges/agrupamentos). */
const TYPE_COLORS: Record<string, string> = {
  BURACO_NA_RUA_OU_CALCADA:              '#ea580c',
  POSTE_COM_LUZ_QUEIMADA:                '#ca8a04',
  LIXO_ACUMULADO_OU_TERRENO_SUJO:        '#15803d',
  SINALIZACAO_OU_SEMAFORO_COM_DEFEITO:   '#b45309',
  PROBLEMAS_EM_PRACAS_E_PARQUES:         '#16a34a',
  FALHAS_NO_TRANSPORTE_PUBLICO:          '#1d4ed8',
  PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA: '#dc2626',
  SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO:    '#7c3aed',
  OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO:   '#6b7280',
  MAUS_TRATOS_AOS_ANIMAIS:               '#db2777',
  PESSOA_PRECISANDO_DE_AJUDA:            '#0d9488',
  OUTROS_PROBLEMAS:                      '#94a3b8',
};

const TYPE_LABELS: Record<string, string> =
  Object.fromEntries(OCCURRENCE_TYPES.map(t => [t.value, t.label]));

const STATUS_LABELS: Record<string, string> = {
  PENDENTE:     'Pendente',
  EM_ANDAMENTO: 'Em Andamento',
  ATENDIDA:     'Atendida',
  INDEFERIDA:   'Indeferida',
};

/** Classe CSS do badge de status (list-occurrence). */
const STATUS_CLASSES: Record<string, string> = {
  PENDENTE:     'badge-pending',
  EM_ANDAMENTO: 'badge-progress',
  ATENDIDA:     'badge-done',
  INDEFERIDA:   'badge-rejected',
};

/** Cor do marcador no mapa por status. */
const STATUS_COLORS: Record<string, string> = {
  ATENDIDA:     '#16a34a',
  EM_ANDAMENTO: '#d97706',
  INDEFERIDA:   '#6b7280',
  PENDENTE:     '#dc2626',
};

const PRIORITY_CLASSES: Record<string, string> = {
  ALTA:  'priority-high',
  MEDIA: 'priority-medium',
  BAIXA: 'priority-low',
};

/** Label da categoria ("Buraco na Rua ou Calçada"). */
export function typeLabel(type?: string): string {
  return type ? (TYPE_LABELS[type] || type) : '';
}

/** Cor da categoria (badges/agrupamentos). */
export function typeColor(type?: string): string {
  return type ? (TYPE_COLORS[type] || '#94a3b8') : '#94a3b8';
}

/** Label do status ("Em Andamento"). */
export function statusLabel(status?: string): string {
  return status ? (STATUS_LABELS[status] || status) : '';
}

/** Classe CSS do badge de status. */
export function statusClass(status?: string): string {
  return status ? (STATUS_CLASSES[status] || '') : '';
}

/** Cor do marcador de mapa pelo status. */
export function statusColor(status?: string): string {
  return status ? (STATUS_COLORS[status] || '#dc2626') : '#dc2626';
}

/** Classe CSS da prioridade (ALTA/MEDIA/BAIXA). */
export function priorityClass(priority?: string): string {
  return priority ? (PRIORITY_CLASSES[priority] || '') : '';
}

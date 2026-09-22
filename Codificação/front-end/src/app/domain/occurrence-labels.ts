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

const TYPE_COLORS: Record<string, string> = {
  BURACO_NA_RUA_OU_CALCADA:              '#9c4a1c',
  POSTE_COM_LUZ_QUEIMADA:                '#7d6a12',
  LIXO_ACUMULADO_OU_TERRENO_SUJO:        '#3d6420',
  SINALIZACAO_OU_SEMAFORO_COM_DEFEITO:   '#8a5800',
  PROBLEMAS_EM_PRACAS_E_PARQUES:         '#176242',
  FALHAS_NO_TRANSPORTE_PUBLICO:          '#14487e',
  PROBLEMAS_EM_POSTO_DE_SAUDE_OU_ESCOLA: '#8b3a2d',
  SOM_ALTO_OU_PERTURBACAO_DO_SOSSEGO:    '#5a4a9c',
  OBRA_IRREGULAR_OU_IMOVEL_ABANDONADO:   '#4a5a73',
  MAUS_TRATOS_AOS_ANIMAIS:               '#993d69',
  PESSOA_PRECISANDO_DE_AJUDA:            '#146b6b',
  OUTROS_PROBLEMAS:                      '#5d6a7c',
};

const TYPE_LABELS: Record<string, string> =
  Object.fromEntries(OCCURRENCE_TYPES.map(t => [t.value, t.label]));

const STATUS_LABELS: Record<string, string> = {
  PENDENTE:     'Pendente',
  EM_ANDAMENTO: 'Em Andamento',
  CONCLUIDA:    'Concluída',
  INDEFERIDA:   'Indeferida',
};

const STATUS_CLASSES: Record<string, string> = {
  PENDENTE:     'badge-pending',
  EM_ANDAMENTO: 'badge-progress',
  CONCLUIDA:    'badge-done',
  INDEFERIDA:   'badge-rejected',
};

const STATUS_COLORS: Record<string, string> = {
  CONCLUIDA:    '#176242',
  EM_ANDAMENTO: '#14487e',
  INDEFERIDA:   '#8b3a2d',
  PENDENTE:     '#8a5800',
};

const PRIORITY_LABELS: Record<string, string> = {
  ALTA:  'Alta',
  MEDIA: 'Média',
  BAIXA: 'Baixa',
};

const PRIORITY_CLASSES: Record<string, string> = {
  ALTA:  'priority-high',
  MEDIA: 'priority-medium',
  BAIXA: 'priority-low',
};

export function typeLabel(type?: string): string {
  return type ? (TYPE_LABELS[type] || type) : '';
}

export function typeColor(type?: string): string {
  return type ? (TYPE_COLORS[type] || '#5d6a7c') : '#5d6a7c';
}

export function statusLabel(status?: string): string {
  return status ? (STATUS_LABELS[status] || status) : '';
}

export function statusClass(status?: string): string {
  return status ? (STATUS_CLASSES[status] || '') : '';
}

export function statusColor(status?: string): string {
  return status ? (STATUS_COLORS[status] || '#8a5800') : '#8a5800';
}
export function priorityClass(priority?: string): string {
  return priority ? (PRIORITY_CLASSES[priority] || '') : '';
}

export function priorityLabel(priority?: string): string {
  return priority ? (PRIORITY_LABELS[priority] || priority) : '';
}

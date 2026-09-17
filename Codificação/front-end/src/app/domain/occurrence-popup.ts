import { Occurrence } from './model/occurrence';
import { statusColor, statusLabel } from './occurrence-labels';

/**
 * Cartão que aparece ao clicar num marcador — o mesmo no mapa da tela inicial
 * e no da ficha. Mora aqui, e não num componente Angular, porque o Leaflet
 * recebe uma string de HTML: o estilo está em styles.css (.occ-popup).
 */
export interface PopupOptions {
  /** É a ocorrência que a tela já mostra: o protocolo vira texto, não atalho. */
  current?: boolean;
  /** Total de apoios, quando já carregado. */
  supportCount?: number;
  /** HTML extra no fim do cartão (o botão de apoiar, no mapa da tela inicial). */
  footer?: string;
}

/**
 * Título, descrição e endereço vêm do cidadão e entram numa string de HTML:
 * sem escapar, um título com `<img onerror=...>` executaria ao abrir o cartão.
 */
export function escapeHtml(text?: string | null): string {
  const mapa: Record<string, string> = {
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  };
  return (text || '').replace(/[&<>"']/g, c => mapa[c]);
}

export function occurrencePopup(o: Occurrence, opts: PopupOptions = {}): string {
  const color     = statusColor(o.status);
  const endereco  = [o.street, o.neighborhood].filter(p => p?.trim()).join(', ');
  const protocolo = opts.current
    ? `<span class="occ-popup__protocol occ-popup__protocol--current">${
        escapeHtml(o.protocolNumber)} (atual)</span>`
    : `<a class="occ-popup__protocol" href="/occurrence/detail/${o.id}"
          target="_blank" rel="noopener"
          title="Abrir a ocorrência ${escapeHtml(o.protocolNumber)} em uma nova aba"
       >${escapeHtml(o.protocolNumber)}</a>`;
  const apoios = opts.supportCount
    ? `<span class="occ-popup__supports">🤝 ${opts.supportCount} ${
        opts.supportCount === 1 ? 'apoio' : 'apoios'}</span>`
    : '';

  return `<div class="occ-popup">
    ${protocolo}
    <p class="occ-popup__title">${escapeHtml(o.title || o.description)}</p>
    ${endereco ? `<p class="occ-popup__place">${escapeHtml(endereco)}</p>` : ''}
    <div class="occ-popup__foot">
      <span class="occ-popup__status"
            style="background:${color}1f;color:${color}">${statusLabel(o.status)}</span>
      ${apoios}
    </div>
    ${opts.footer || ''}
  </div>`;
}

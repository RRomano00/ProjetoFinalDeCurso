import { Router } from '@angular/router';

// Cards de ocorrência são <div>, não <a>: dentro de link não dá para arrastar e selecionar o texto.
export function openOccurrence(router: Router, id: number | undefined, e: Event) {
  if (window.getSelection()?.toString()) return;  // arrastou para selecionar: não abre
  const path = ['/occurrence/detail', id];
  const m = e as MouseEvent;
  if (m.ctrlKey || m.metaKey || m.button === 1) {
    window.open(router.serializeUrl(router.createUrlTree(path)), '_blank', 'noopener');
  } else if (e.type !== 'auxclick') {
    router.navigate(path);
  }
}

import { Directive, ElementRef, OnInit, Renderer2 } from '@angular/core';

const ICONE = (aberto: boolean) => `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
  viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
  stroke-linecap="round" stroke-linejoin="round">` + (aberto
    ? `<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/>
       <path d="M10.73 5.08A10.4 10.4 0 0 1 12 5c7 0 10 7 10 7a13.2 13.2 0 0 1-1.67 2.68"/>
       <path d="M6.61 6.61A13.5 13.5 0 0 0 2 12s3 7 10 7a9.7 9.7 0 0 0 5.39-1.61"/>
       <line x1="2" x2="22" y1="2" y2="22"/>`
    : `<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>`)
  + `</svg>`;

/**
 * Olho de mostrar/ocultar em todo campo de senha.
 *
 * O seletor pega qualquer `input[type=password]`, então o template não muda:
 * basta importar a diretiva no componente. O botão nasce aqui, com estilo
 * inline — elemento criado em tempo de execução não recebe o atributo de
 * encapsulamento do Angular e ficaria de fora do CSS do componente.
 */
@Directive({ selector: 'input[type=password]' })
export class PasswordRevealDirective implements OnInit {

  constructor(private el: ElementRef<HTMLInputElement>, private renderer: Renderer2) {}

  ngOnInit() {
    const input = this.el.nativeElement;
    const r = this.renderer;

    // Ícones já posicionados na moldura do campo (o cadeado à esquerda) ficam
    // atrás da caixa nova, que é posicionada e vem depois deles na árvore.
    // O z-index os traz de volta — em elemento estático, como o rótulo, não faz nada.
    const parente = input.parentElement;
    if (parente) Array.from(parente.children).forEach(irmao => r.setStyle(irmao, 'z-index', '1'));

    // Envolve o input para ancorar o botão, seja qual for o layout da tela.
    const caixa = r.createElement('span');
    r.setStyle(caixa, 'position', 'relative');
    r.setStyle(caixa, 'display', 'block');
    r.insertBefore(r.parentNode(input), caixa, input);
    r.appendChild(caixa, input);
    r.setStyle(input, 'padding-right', '2.5rem');

    const botao = r.createElement('button');
    r.setAttribute(botao, 'type', 'button');   // dentro de <form>: não submete
    r.setAttribute(botao, 'tabindex', '-1');   // fora do caminho do Tab
    for (const [prop, valor] of Object.entries({
      position: 'absolute', right: '0.625rem', top: '50%', transform: 'translateY(-50%)',
      display: 'flex', padding: '0', border: 'none', background: 'none',
      color: 'var(--ink-faint)', cursor: 'pointer'
    })) r.setStyle(botao, prop, valor);
    r.appendChild(caixa, botao);

    const pintar = () => {
      const aberto = input.type === 'text';
      botao.innerHTML = ICONE(aberto);
      const rotulo = aberto ? 'Ocultar senha' : 'Mostrar senha';
      r.setAttribute(botao, 'aria-label', rotulo);
      r.setAttribute(botao, 'title', rotulo);
    };

    r.listen(botao, 'click', () => {
      r.setProperty(input, 'type', input.type === 'text' ? 'password' : 'text');
      pintar();
    });
    pintar();
  }
}

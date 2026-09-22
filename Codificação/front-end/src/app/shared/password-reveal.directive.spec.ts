import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PasswordRevealDirective } from './password-reveal.directive';

@Component({
  imports: [PasswordRevealDirective],
  template: `
    <!-- Molde das telas de conta: ícone à esquerda dentro da moldura. -->
    <div class="input-wrapper">
      <span class="input-icon">cadeado</span>
      <input type="password" id="comIcone" />
    </div>
    <!-- Molde do perfil e do cadastro de usuário: input solto no grupo. -->
    <div class="field-group">
      <label for="solto">Senha</label>
      <input type="password" id="solto" class="form-input" />
    </div>
  `
})
class HostDeTeste {}

describe('PasswordRevealDirective', () => {
  let raiz: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostDeTeste] }).compileComponents();
    const fixture = TestBed.createComponent(HostDeTeste);
    fixture.detectChanges();
    raiz = fixture.nativeElement;
  });

  it('põe um botão em cada campo de senha', () => {
    expect(raiz.querySelectorAll('button[aria-label="Mostrar senha"]').length).toBe(2);
  });

  for (const id of ['comIcone', 'solto']) {
    it(`mostra e esconde a senha do campo #${id}`, () => {
      const input = raiz.querySelector<HTMLInputElement>(`#${id}`)!;
      const botao = input.parentElement!.querySelector('button')!;

      expect(input.type).toBe('password');
      botao.click();
      expect(input.type).toBe('text');
      expect(botao.getAttribute('aria-label')).toBe('Ocultar senha');
      botao.click();
      expect(input.type).toBe('password');
    });
  }

  it('não esconde o ícone que já estava na moldura do campo', () => {
    const icone = raiz.querySelector<HTMLElement>('.input-icon')!;
    expect(icone.style.zIndex).toBe('1');
  });
});

# FalaCidade — front-end

Angular 19. Para subir tudo junto, use `../scripts/subir.sh` (ver `../scripts/INICIAR-SERVIDORES.md`).

```bash
npm install
ng serve                  # http://localhost:4200; /api vai para a 8080 (proxy.conf.json)
npm run build             # dist/fala-cidade/browser; sirva com ../scripts/servir.mjs
ng test --watch=false     # testes unitários (Karma)
```

- Endereço da API: `src/environments/environment.ts` aponta para `/api`; nada de URL do backend no bundle.
- Configuração do backend (banco, e-mail, Cloudinary, senha de demonstração): `../scripts/INICIAR-SERVIDORES.md`.

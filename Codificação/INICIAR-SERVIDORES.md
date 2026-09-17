# Como iniciar o FalaCidade

Guia rápido para subir back-end + front-end, tanto no PC quanto no celular.

---

## Atalho: um comando só (Linux/macOS/WSL)

```bash
cd Codificação
./subir.sh              # PC     -> http://localhost:4173 (com PWA/service worker)
./subir.sh --celular    # celular -> abre os 2 túneis e imprime a URL
```

O script sobe o backend (`mvnw spring-boot:run`), abre o túnel do backend, escreve a
URL no `environment.ts`, roda o `npm run build`, serve o `dist` e abre o túnel do front.
`Ctrl+C` derruba tudo e **restaura o `environment.ts` original**. Logs em `/tmp/falacidade/`.
Se o backend já estiver rodando (IntelliJ), ele detecta e não sobe outro.

**Endereço do celular (fixo)** — `./subir.sh --celular` já abre o front no domínio
reservado no ngrok, que não muda entre execuções:

```
https://duration-dismiss-pacifist.ngrok-free.dev
```

Ele está no alto do `subir.sh`, na variável `DOMINIO`. Para trocar, reserve outro em
*dashboard.ngrok.com → Domains* (o plano free dá 1 domínio permanente e é ele quem
escolhe o nome) e edite a linha — ou passe na hora: `DOMINIO=outro.ngrok-free.dev
./subir.sh --celular`. Para voltar ao Cloudflare, com URL aleatória: `DOMINIO=
./subir.sh --celular`.

Na primeira visita o ngrok free mostra uma tela de aviso com o botão *Visit Site*; como o
domínio é fixo, o cookie fica salvo e ela não volta a aparecer nesse navegador.
Só o front usa domínio fixo — o túnel do backend continua aleatório, mas o script já o
injeta sozinho no `environment.ts`. O CORS do backend aceita `*.ngrok-free.dev`,
`*.ngrok-free.app` e `*.trycloudflare.com`.
Outra porta local: `PORTA=8081 ./subir.sh`.

Os passos manuais abaixo continuam válidos (Windows/IntelliJ, ou para depurar).

---

## A) Rodar só no PC (desenvolvimento)

1. **Backend** — abra o projeto no IntelliJ e clique em **Run** (porta `8080`).
2. **Frontend** — no terminal, dentro de `Codificação/front-end/`:
   ```powershell
   ng serve
   ```
3. Acesse: **http://localhost:4200**

> Confirme que `src/environments/environment.ts` aponta para `http://localhost:8080/api`.
> O modo `ng serve` **não** ativa o PWA/service worker — use a opção B para testar PWA.

---

## B) Acessar no celular (via Cloudflare HTTPS)

A ordem importa: o backend precisa estar no ar **antes** de buildar o front.

### 1. Backend
IntelliJ → **Run** (porta `8080`).

### 2. Túnel do backend — Terminal 1
```powershell
npx cloudflared tunnel --url http://localhost:8080
```
Copie a URL gerada (ex.: `https://abc-def-123.trycloudflare.com`).

### 3. Atualizar a URL da API
Em `Codificação/front-end/src/environments/environment.ts`, cole a URL do passo 2:
```ts
api_endpoint: 'https://abc-def-123.trycloudflare.com/api',
authentication_api_endpoint: 'https://abc-def-123.trycloudflare.com/api'
```

### 4. Build do front — Terminal 2 (dentro de `Codificação/front-end/`)
```powershell
npm run build
```
Gera os arquivos em `dist/fala-cidade/browser/`.

### 5. Servir o build — Terminal 2 (mesmo terminal, após o build)
```powershell
npx serve -s dist/fala-cidade/browser -l 4173
```
- `-s` → modo **SPA**: faz as rotas do Angular funcionarem no refresh (sem erro 404)
- `-l 4173` → porta 4173

### 6. Túnel do front — Terminal 3
```powershell
npx cloudflared tunnel --url http://127.0.0.1:4173
```
Abra **no celular** a URL que este comando gerar.

---

## Resumo dos terminais (opção B)

| Onde      | Comando |
|-----------|---------|
| IntelliJ  | Backend (Run) na porta 8080 |
| Terminal 1| `npx cloudflared tunnel --url http://localhost:8080` |
| Terminal 2| `npm run build` e depois `npx serve -s dist/fala-cidade/browser -l 4173` |
| Terminal 3| `npx cloudflared tunnel --url http://127.0.0.1:4173` |

---

## Observações importantes

- **As URLs do Cloudflare mudam toda vez** que você reinicia os túneis. Se isso acontecer, repita os passos 2 → 3 → 4 → 5 → 6 (nova URL no `environment.ts` + novo build).
- O **CORS** já aceita `*.trycloudflare.com`, então o backend **não** precisa reiniciar quando só a URL do front muda — apenas quando a URL do **backend** muda.
- Para ver mudanças novas no celular, recarregue a página (o service worker pode segurar a versão antiga — recarregue 2x ou use Ctrl+Shift+R no PC).
- Sempre rode os comandos `npm run build` e `npx serve` **de dentro de `Codificação/front-end/`**.

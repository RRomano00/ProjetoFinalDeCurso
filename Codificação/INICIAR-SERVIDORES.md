# Como iniciar o FalaCidade

Guia rápido para subir back-end + front-end, tanto no PC quanto no celular.

---

## Atalho: um comando só (Linux/macOS/WSL)

```bash
cd Codificação
./subir.sh              # PC     -> http://localhost:4173 (com PWA/service worker)
./subir.sh --celular    # celular -> abre o túnel e imprime a URL
```

**Endereço do celular (fixo)** — `./subir.sh --celular` já abre o front no domínio
reservado no ngrok, que não muda entre execuções:

```
https://duration-dismiss-pacifist.ngrok-free.dev
```

---

## Atalho no Windows (sem WSL)

```powershell
cd Codificação
.\subir.ps1              # PC     -> http://localhost:4173
.\subir.ps1 -Celular     # celular -> mesmo domínio fixo do ngrok
```

Mesmo fluxo do `subir.sh`. Precisa de Java 21+, Node 20+, PostgreSQL e ngrok no PATH.
Se o Windows barrar o script: `Set-ExecutionPolicy -Scope Process RemoteSigned`.
Logs em `%TEMP%\falacidade\`. Prefira o PowerShell 7 — no 5.1 o `Ctrl+C` pode não
derrubar o túnel, e túnel órfão segura o domínio do ngrok (plano free aceita uma sessão só).

O domínio fixo é da **conta** do ngrok, não da máquina: mesmo authtoken, mesma URL no
Windows e no Linux.

Os passos manuais abaixo continuam válidos (IntelliJ, ou para depurar).

---

## A) Rodar só no PC (desenvolvimento)

1. **Backend** — abra o projeto no IntelliJ e clique em **Run** (porta `8080`).
2. **Frontend** — no terminal, dentro de `Codificação/front-end/`:
   ```powershell
   ng serve
   ```
3. Acesse: **http://localhost:4200**

> O `environment.ts` aponta para `/api`, e quem repassa isso para a porta 8080 no
> `ng serve` é o `front-end/proxy.conf.json` (já ligado no `angular.json`).
> O modo `ng serve` **não** ativa o PWA/service worker — use a opção B para testar PWA.

---

## B) Acessar no celular (via Cloudflare HTTPS)

A ordem importa: o backend precisa estar no ar **antes** de buildar o front.

### 1. Backend
IntelliJ → **Run** (porta `8080`).

### 2. Build do front — Terminal 1 (dentro de `Codificação/front-end/`)
```powershell
npm run build
```
Gera os arquivos em `dist/fala-cidade/browser/`.

### 3. Servir o build — Terminal 1 (mesmo terminal, após o build; agora a partir de `Codificação/`)
```powershell
node servir.mjs front-end/dist/fala-cidade/browser 4173 127.0.0.1:8080
```
- entrega o build e manda `/api` para a porta 8080 — mesma origem, sem CORS
- qualquer outra rota cai no `index.html`, para o refresh do Angular não dar 404

### 4. Túnel — Terminal 2
```powershell
npx cloudflared tunnel --url http://127.0.0.1:4173
```
Abra **no celular** a URL que este comando gerar.

---

## Observações importantes

- **A URL do Cloudflare muda toda vez** que você reinicia o túnel, mas isso não obriga mais a rebuildar: o endereço da API não entra no bundle.
- O **CORS** saiu do caminho — front e API estão na mesma origem.
- Para ver mudanças novas no celular, recarregue a página. O app já recarrega sozinho quando o service worker termina de baixar uma versão nova; se quiser forçar, Ctrl+Shift+R no PC.
- O `npm run build` roda de dentro de `Codificação/front-end/`; o `node servir.mjs`, de `Codificação/`.

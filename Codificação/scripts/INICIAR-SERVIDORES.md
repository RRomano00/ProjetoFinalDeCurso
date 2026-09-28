# Como iniciar o FalaCidade

Guia rápido para subir back-end + front-end, tanto no PC quanto no celular.

---

## Atalho: um comando só (Linux/macOS/WSL)

```bash
cd Codificação
./scripts/subir.sh              # PC     -> http://localhost:4173 (com PWA/service worker)
./scripts/subir.sh --celular    # celular -> abre o túnel e imprime a URL
```

**Endereço do celular (fixo)** — `./scripts/subir.sh --celular` já abre o front no domínio
reservado no ngrok, que não muda entre execuções:

```
https://duration-dismiss-pacifist.ngrok-free.dev
```

---

## Atalho no Windows (sem WSL)

```powershell
cd Codificação
.\scripts\subir.ps1              # PC     -> http://localhost:4173
.\scripts\subir.ps1 -Celular     # celular -> mesmo domínio fixo do ngrok
```

Mesmo fluxo do `scripts/subir.sh`. Precisa de Java 21+, Node 20+, PostgreSQL e ngrok no PATH.
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

### 3. Servir o build — Terminal 1 (mesmo terminal, após o build; a partir de `Codificação/`)
```powershell
node scripts/servir.mjs front-end/dist/fala-cidade/browser 4173 127.0.0.1:8080
```
- entrega o build e manda `/api` para a porta 8080 — mesma origem, sem CORS
- qualquer outra rota cai no `index.html`, para o refresh do Angular não dar 404

### 4. Túnel — Terminal 2
```powershell
npx cloudflared tunnel --url http://127.0.0.1:4173
```
Abra **no celular** a URL que este comando gerar.

---

## SMS do 2FA (gateway Android)

O código sai de um celular Android com o app [SMS Gateway for Android](https://github.com/capcom6/android-sms-gateway/releases) (chip com SMS ilimitado).

No celular: permissão de SMS, bateria **sem restrições**, ligar **Cloud Server** (ou **Local Server**) e tocar em **Offline** → **Online**.

Testar o envio (usuário e senha aparecem no app):
```bash
curl -i -X POST -u "USUARIO:SENHA" -H "Content-Type: application/json" \
  -d '{"textMessage":{"text":"FalaCidade: teste"},"phoneNumbers":["+55DDDNUMERO"]}' \
  https://api.sms-gate.app/3rdparty/v1/messages          # Local Server: http://<ip-do-celular>:8080/message
```

No `application.properties` local (nunca commitar os valores):
```properties
app.sms.enabled=true
app.sms.url=https://api.sms-gate.app/3rdparty/v1/messages
app.sms.username=USUARIO
app.sms.password=SENHA
```
- `enabled=false`: quem só tem SMS recebe o código por e-mail.
- Aviso do Android "enviando muitas mensagens": tocar em **Sempre permitir**.

---

## Observações importantes

- **A URL do Cloudflare muda toda vez** que você reinicia o túnel, mas isso não obriga mais a rebuildar: o endereço da API não entra no bundle.
- O **CORS** saiu do caminho — front e API estão na mesma origem.
- Para ver mudanças novas no celular, recarregue a página. O app já recarrega sozinho quando o service worker termina de baixar uma versão nova; se quiser forçar, Ctrl+Shift+R no PC.
- O `npm run build` roda de dentro de `Codificação/front-end/`; o `node scripts/servir.mjs`, de `Codificação/`.

---

## O que tem nesta pasta

| | |
|---|---|
| `subir.sh` | sobe tudo no Linux/macOS/WSL |
| `subir.ps1` | o mesmo no Windows, sem WSL |
| `servir.mjs` | serve o build e repassa `/api` para a 8080 |
| `servir.test.mjs` | `node --test scripts/servir.test.mjs` |

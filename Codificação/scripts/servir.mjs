import { createServer, request } from 'node:http';
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { join, extname, resolve, sep } from 'node:path';

const [dist, porta = '4173', alvo = '127.0.0.1:8080'] = process.argv.slice(2);
const raiz = resolve(dist);
const [apiHost, apiPorta] = alvo.split(':');

const TEXTO = { 'Content-Type': 'text/plain; charset=utf-8' };
const TIPO = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.css': 'text/css', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.webp': 'image/webp', '.avif': 'image/avif', '.gif': 'image/gif', '.ico': 'image/x-icon',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.otf': 'font/otf',
  '.txt': 'text/plain; charset=utf-8', '.map': 'application/json',
};

createServer((req, res) => {
  const caminho = new URL(req.url, 'http://x').pathname;

  if (caminho === '/api' || caminho.startsWith('/api/')) {
    const acima = request(
      { host: apiHost, port: apiPorta, path: req.url, method: req.method, headers: req.headers },
      r => { res.writeHead(r.statusCode, r.headers); r.pipe(res); }
    );
    acima.on('error', () => { res.writeHead(502, TEXTO); res.end('back-end fora do ar'); });
    req.pipe(acima);
    return;
  }

  // arquivo do build; qualquer outra coisa cai no index.html, senao rota do
  // Angular quebra no refresh (era o que o `serve -s` fazia).
  let arquivo = resolve(join(raiz, decodeURIComponent(caminho)));
  if (arquivo !== raiz && !arquivo.startsWith(raiz + sep)) arquivo = raiz;   // nada de ../

  stat(arquivo)
    .then(s => (s.isFile() ? arquivo : join(raiz, 'index.html')))
    .catch(() => join(raiz, 'index.html'))
    .then(f => {
      res.writeHead(200, { 'Content-Type': TIPO[extname(f)] ?? 'application/octet-stream' });
      createReadStream(f).on('error', () => res.end()).pipe(res);
    });
}).listen(Number(porta), () => console.log(`servindo ${raiz} na ${porta} — /api -> ${alvo}`));

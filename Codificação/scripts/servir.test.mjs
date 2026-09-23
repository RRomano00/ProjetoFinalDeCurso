// node --test servir.test.mjs
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, writeFile, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SERVIR = fileURLToPath(new URL('./servir.mjs', import.meta.url));

const PORTA = 4399, API = 4398;
let servidor, backend, dist;

const pegar = (caminho, init) => fetch(`http://127.0.0.1:${PORTA}${caminho}`, init);

before(async () => {
  dist = await mkdtemp(join(tmpdir(), 'servir-'));
  await writeFile(join(dist, 'index.html'), '<h1>index</h1>');
  await writeFile(join(dist, 'main.js'), 'console.log(1)');
  await mkdir(join(dist, 'sub'));
  await writeFile(join(dist, 'sub', 'a.css'), 'b{}');
  await writeFile(join(tmpdir(), 'segredo-servir.txt'), 'NAO PODE VAZAR');

  backend = spawn(process.execPath, ['-e',
    `require('http').createServer((q,s)=>{s.writeHead(200,{'Content-Type':'application/json'});s.end(JSON.stringify({url:q.url,metodo:q.method}))}).listen(${API})`]);
  servidor = spawn(process.execPath, [SERVIR, dist, String(PORTA), `127.0.0.1:${API}`]);
  for (let i = 0; i < 50; i++) {
    try { await pegar('/'); break; } catch { await new Promise(r => setTimeout(r, 100)); }
  }
});

after(() => { servidor?.kill(); backend?.kill(); });

test('serve arquivo do build com o Content-Type certo', async () => {
  const r = await pegar('/main.js');
  assert.equal(r.status, 200);
  assert.match(r.headers.get('content-type'), /javascript/);
  assert.equal(await r.text(), 'console.log(1)');
});

test('rota do Angular cai no index.html (refresh nao pode dar 404)', async () => {
  const r = await pegar('/ocorrencias/42');
  assert.equal(r.status, 200);
  assert.equal(await r.text(), '<h1>index</h1>');
});

test('/api e repassado ao back-end preservando caminho e metodo', async () => {
  const r = await pegar('/api/authenticate', { method: 'POST', body: '{}' });
  assert.deepEqual(await r.json(), { url: '/api/authenticate', metodo: 'POST' });
});

test('back-end fora do ar vira 502, nao index.html disfarcado de sucesso', async () => {
  backend.kill();
  await new Promise(r => setTimeout(r, 200));
  assert.equal((await pegar('/api/qualquer')).status, 502);
});

test('nao serve arquivo fora do dist', async () => {
  for (const alvo of ['/../segredo-servir.txt', '/%2e%2e%2fsegredo-servir.txt', '/sub/../../segredo-servir.txt']) {
    const corpo = await (await pegar(alvo)).text();
    assert.doesNotMatch(corpo, /NAO PODE VAZAR/, `vazou em ${alvo}`);
  }
});

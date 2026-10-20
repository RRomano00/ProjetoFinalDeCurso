import { jwtPayload } from './authentication.service';

// Como o back-end assina: JSON em UTF-8, codificado em base64url e sem "=".
const token = (payload: object) => {
  const bytes = new TextEncoder().encode(JSON.stringify(payload));
  const b64 = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `cabecalho.${b64}.assinatura`;
};

describe('jwtPayload', () => {
  it('devolve os acentos do nome como foram gravados', () => {
    expect(jwtPayload(token({ fullname: 'João Conceição Lúcia' }))['fullname']).toBe('João Conceição Lúcia');
  });

  it('aceita os caracteres do base64url ("-" e "_")', () => {
    // "~~~" e "???" viram "fn5-" e "Pz8_" em base64url.
    expect(jwtPayload(token({ a: '~~~', b: '???' }))).toEqual({ a: '~~~', b: '???' });
  });
});

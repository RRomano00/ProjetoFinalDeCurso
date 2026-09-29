import { chromeIntentUrl, isSamsungInternet } from './install.service';

describe('instalação pelo Chrome a partir do Samsung Internet', () => {
  it('reconhece o Samsung Internet pelo user agent, e só ele', () => {
    expect(isSamsungInternet('Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/27.0 Chrome/125.0.0.0 Mobile Safari/537.36')).toBeTrue();
    expect(isSamsungInternet('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36')).toBeFalse();
    expect(isSamsungInternet('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1')).toBeFalse();
  });

  it('monta o endereço que abre a mesma página no Chrome', () => {
    expect(chromeIntentUrl({ host: 'miss-pacifist.ngrok-free.dev', pathname: '/user/list', search: '?a=1' }))
      .toBe('intent://miss-pacifist.ngrok-free.dev/user/list?a=1#Intent;scheme=https;package=com.android.chrome;end');
  });
});

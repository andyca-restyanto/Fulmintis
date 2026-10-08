// filepath: /frontend/tests/tokenStorage.test.ts
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { installLocalStorage, fakeJwt, inSeconds } from './helpers.ts';

const store = installLocalStorage();
const { tokenStorage } = await import('../src/shared/services/tokenStorage.ts');

beforeEach(() => store.clear());

test('getRole membaca klaim role ADMIN dan USER', () => {
  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', role: 'ADMIN', exp: inSeconds(3600) }));
  assert.equal(tokenStorage.getRole(), 'ADMIN');

  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', role: 'USER', exp: inSeconds(3600) }));
  assert.equal(tokenStorage.getRole(), 'USER');
});

test('token lama tanpa klaim role dianggap USER; nilai role aneh juga USER', () => {
  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', exp: inSeconds(3600) }));
  assert.equal(tokenStorage.getRole(), 'USER');

  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', role: 'SUPERADMIN', exp: inSeconds(3600) }));
  assert.equal(tokenStorage.getRole(), 'USER');
});

test('tanpa token atau token rusak -> role null', () => {
  assert.equal(tokenStorage.getRole(), null);
  tokenStorage.setToken('bukan-jwt');
  assert.equal(tokenStorage.getRole(), null);
  tokenStorage.setToken('a.%%%.c');
  assert.equal(tokenStorage.getRole(), null);
});

test('perilaku lama tidak berubah: subject, kedaluwarsa, hasValidToken', () => {
  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', role: 'ADMIN', exp: inSeconds(3600) }));
  assert.equal(tokenStorage.getTokenSubject(), 'a@b.com');
  assert.equal(tokenStorage.isTokenExpired(), false);
  assert.equal(tokenStorage.hasValidToken(), true);

  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', exp: inSeconds(-60) }));
  assert.equal(tokenStorage.isTokenExpired(), true);
  assert.equal(tokenStorage.hasValidToken(), false);
  assert.equal(tokenStorage.getToken(), null); // token kedaluwarsa dibuang

  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com' })); // tanpa exp
  assert.equal(tokenStorage.isTokenExpired(), true);
});

test('token yang tinggal beberapa detik dianggap habis (toleransi selisih jam)', () => {
  tokenStorage.setToken(fakeJwt({ sub: 'a@b.com', exp: inSeconds(5) }));
  assert.equal(tokenStorage.isTokenExpired(), true);
});

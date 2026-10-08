// filepath: /frontend/tests/guards.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolveGuard, homeRouteFor, signinRouteFor, type GuardRoute, type GuardSession } from '../src/router/guards.ts';

const none: GuardSession = { hadToken: false, hasValidToken: false, role: null };
const expired: GuardSession = { hadToken: true, hasValidToken: false, role: null };
const user: GuardSession = { hadToken: true, hasValidToken: true, role: 'USER' };
const admin: GuardSession = { hadToken: true, hasValidToken: true, role: 'ADMIN' };

const userPage = (name = 'dashboard'): GuardRoute => ({ name, requiresAuth: true, role: null, hasNotice: false });
const adminPage = (name = 'admin-dashboard'): GuardRoute => ({ name, requiresAuth: true, role: 'ADMIN', hasNotice: false });
const publicPage = (name: string, hasNotice = false): GuardRoute => ({ name, requiresAuth: false, role: null, hasNotice });

test('tanpa token: halaman user -> sign in user, halaman admin -> sign in admin', () => {
  assert.deepEqual(resolveGuard(userPage(), none), { name: 'auth-signin' });
  assert.deepEqual(resolveGuard(adminPage(), none), { name: 'admin-signin' });
});

test('token kedaluwarsa: diarahkan ke sign in area yang sama dengan pesan expired', () => {
  assert.deepEqual(resolveGuard(userPage(), expired), { name: 'auth-signin', query: { expired: 'true' } });
  assert.deepEqual(resolveGuard(adminPage(), expired), { name: 'admin-signin', query: { expired: 'true' } });
});

test('user biasa boleh halaman user, TIDAK boleh halaman admin', () => {
  assert.equal(resolveGuard(userPage(), user), true);
  assert.deepEqual(resolveGuard(adminPage(), user), { name: 'dashboard' });
  assert.deepEqual(resolveGuard(adminPage('admin-invite'), user), { name: 'dashboard' });
});

test('admin boleh halaman admin, TIDAK boleh dashboard user maupun halaman project', () => {
  assert.equal(resolveGuard(adminPage(), admin), true);
  assert.deepEqual(resolveGuard(userPage('dashboard'), admin), { name: 'admin-dashboard' });
  assert.deepEqual(resolveGuard(userPage('project-dashboard'), admin), { name: 'admin-dashboard' });
});

test('route yang butuh login tanpa meta.role dianggap area USER', () => {
  const route: GuardRoute = { name: 'project-settings', requiresAuth: true, role: null, hasNotice: false };
  assert.equal(resolveGuard(route, user), true);
  assert.deepEqual(resolveGuard(route, admin), { name: 'admin-dashboard' });
});

test('token tanpa klaim role dianggap USER', () => {
  const legacy: GuardSession = { hadToken: true, hasValidToken: true, role: null };
  assert.equal(resolveGuard(userPage(), legacy), true);
  assert.deepEqual(resolveGuard(adminPage(), legacy), { name: 'dashboard' });
});

test('yang sudah login dilempar dari halaman sign in/up ke dashboard perannya', () => {
  for (const page of ['auth-signin', 'auth-signup', 'admin-signin', 'admin-signup']) {
    assert.deepEqual(resolveGuard(publicPage(page), user), { name: 'dashboard' }, `user di ${page}`);
    assert.deepEqual(resolveGuard(publicPage(page), admin), { name: 'admin-dashboard' }, `admin di ${page}`);
  }
});

test('halaman sign in dengan query notifikasi tetap dibuka walau sudah login', () => {
  assert.equal(resolveGuard(publicPage('auth-signin', true), user), true);
  assert.equal(resolveGuard(publicPage('admin-signin', true), admin), true);
});

test('halaman publik non-entry tetap terbuka untuk semua peran', () => {
  for (const page of ['auth-forgot-password', 'auth-reset-password', 'admin-accept-invitation', 'admin-reset-password']) {
    for (const session of [none, expired, user, admin]) {
      assert.equal(resolveGuard(publicPage(page), session), true, `${page}`);
    }
  }
  assert.equal(resolveGuard(publicPage('auth-signin'), none), true);
  assert.equal(resolveGuard({ name: null, requiresAuth: false, role: null, hasNotice: false }, admin), true);
});

test('pemetaan nama route per peran', () => {
  assert.equal(homeRouteFor('ADMIN'), 'admin-dashboard');
  assert.equal(homeRouteFor('USER'), 'dashboard');
  assert.equal(signinRouteFor('ADMIN'), 'admin-signin');
  assert.equal(signinRouteFor('USER'), 'auth-signin');
});

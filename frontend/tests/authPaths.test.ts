// filepath: /frontend/tests/authPaths.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isPublicAuthPath, signinPathForRole } from '../src/shared/services/authPaths.ts';

test('halaman publik user dan admin dikenali (tidak memicu redirect 401 berulang)', () => {
  for (const path of [
    '/auth', '/auth/signin', '/auth/reset-password',
    '/admin/signin', '/admin/signup', '/admin/accept-invitation', '/admin/reset-password',
  ]) {
    assert.equal(isPublicAuthPath(path), true, path);
  }
});

test('area terproteksi dan path mirip BUKAN halaman publik', () => {
  for (const path of [
    '/', '/dashboard', '/projects/1/dashboard',
    '/admin', '/admin/dashboard', '/admin/admins/new', '/admin/users',
    '/authors', '/admin/signing', '/admin/signin-extra',
  ]) {
    assert.equal(isPublicAuthPath(path), false, path);
  }
});

test('URL sign in mengikuti peran sesi', () => {
  assert.equal(signinPathForRole('ADMIN'), '/admin/signin');
  assert.equal(signinPathForRole('USER'), '/auth/signin');
  assert.equal(signinPathForRole(null), '/auth/signin');
});

// filepath: /frontend/tests/adminMenu.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ADMIN_MENU, isMenuActive } from '../src/modules/admin/utils/adminMenu.ts';
import adminRoutes from '../src/modules/admin/routes.ts';

const children = adminRoutes.find((r) => r.path === '/admin')?.children ?? [];
const childNames = new Set(children.map((c) => c.name).filter((n): n is string => typeof n === 'string'));

test('menu Admin berada PERTAMA, sebelum menu User', () => {
  const labels = ADMIN_MENU.map((m) => m.label);
  assert.equal(labels[0], 'Admin');
  assert.ok(labels.indexOf('Admin') < labels.indexOf('User'));
  assert.deepEqual(labels, ['Admin', 'User', 'Log user', 'Payment', 'AI token used']);
});

test('setiap menu menunjuk route admin yang ada, begitu juga route turunannya', () => {
  for (const entry of ADMIN_MENU) {
    assert.ok(childNames.has(entry.routeName), entry.routeName);
    for (const extra of entry.activeFor ?? []) assert.ok(childNames.has(extra), extra);
  }
});

test('halaman undang admin tetap menyorot menu Admin, tidak menu lain', () => {
  const active = ADMIN_MENU.filter((m) => isMenuActive(m, 'admin-invite')).map((m) => m.label);
  assert.deepEqual(active, ['Admin']);
});

test('hanya satu menu aktif per route, dan tidak ada untuk nama bukan string', () => {
  assert.deepEqual(ADMIN_MENU.filter((m) => isMenuActive(m, 'admin-users')).map((m) => m.label), ['User']);
  assert.deepEqual(ADMIN_MENU.filter((m) => isMenuActive(m, 'admin-dashboard')), []);
  assert.equal(ADMIN_MENU.some((m) => isMenuActive(m, undefined)), false);
  assert.equal(ADMIN_MENU.some((m) => isMenuActive(m, Symbol('x'))), false);
});

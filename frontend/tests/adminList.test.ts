// filepath: /frontend/tests/adminList.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  ADMIN_PAGE_SIZE, adminDisplayName, clampPage, formatAdminDate, isInvitationExpired,
  pageIndexFromQuery, pageQueryFromIndex, pageSummary, resendInviteRequest, rowActions, statusLabel,
} from '../src/modules/admin/utils/adminList.ts';
import type { AdminStatus } from '../src/modules/admin/types/admin-auth.types.ts';

const row = (status: AdminStatus, self = false, invitationExpiresAt: string | null = null) =>
  ({ status, self, invitationExpiresAt });

test('rowActions: baris sendiri tidak punya aksi apa pun', () => {
  for (const s of ['ACTIVE', 'PENDING', 'INACTIVE'] as const) assert.deepEqual(rowActions(row(s, true, '2030-01-01T00:00:00')), []);
});

test('rowActions per status', () => {
  assert.deepEqual(rowActions(row('ACTIVE')), ['deactivate']);
  assert.deepEqual(rowActions(row('INACTIVE')), ['activate']);
  assert.deepEqual(rowActions(row('PENDING', false, '2030-01-01T00:00:00')), ['resend', 'deactivate']);
  // admin pertama belum verifikasi email: tidak ada undangan untuk dikirim ulang
  assert.deepEqual(rowActions(row('PENDING')), ['deactivate']);
});

test('statusLabel', () => {
  const now = Date.parse('2030-06-01T00:00:00Z');
  assert.deepEqual(statusLabel(row('ACTIVE'), now), { text: 'Aktif', tone: 'success' });
  assert.deepEqual(statusLabel(row('INACTIVE'), now), { text: 'Nonaktif', tone: 'muted' });
  assert.deepEqual(statusLabel(row('PENDING'), now), { text: 'Menunggu verifikasi email', tone: 'warning' });
  assert.deepEqual(statusLabel(row('PENDING', false, '2030-06-02T00:00:00Z'), now), { text: 'Menunggu undangan', tone: 'warning' });
  assert.deepEqual(statusLabel(row('PENDING', false, '2030-05-31T00:00:00Z'), now), { text: 'Undangan kedaluwarsa', tone: 'danger' });
});

test('isInvitationExpired: batas tepat dianggap kedaluwarsa; null/tidak valid tidak', () => {
  const t = '2030-06-01T00:00:00Z';
  assert.equal(isInvitationExpired(t, Date.parse(t)), true);
  assert.equal(isInvitationExpired(t, Date.parse(t) - 1), false);
  assert.equal(isInvitationExpired(null, 0), false);
  assert.equal(isInvitationExpired('bukan tanggal', 0), false);
});

test('nama tampilan & body kirim ulang undangan jatuh ke email bila nama kosong', () => {
  assert.equal(adminDisplayName({ name: ' Budi ', email: 'b@x.id' }), 'Budi');
  assert.equal(adminDisplayName({ name: null, email: 'b@x.id' }), 'b@x.id');
  assert.equal(adminDisplayName({ name: '   ', email: 'b@x.id' }), 'b@x.id');
  assert.deepEqual(resendInviteRequest({ name: null, email: 'b@x.id' }), { name: 'b@x.id', email: 'b@x.id' });
  assert.deepEqual(resendInviteRequest({ name: 'Budi', email: 'b@x.id' }), { name: 'Budi', email: 'b@x.id' });
});

test('clampPage', () => {
  assert.equal(ADMIN_PAGE_SIZE, 10);
  assert.equal(clampPage(5, 3), 2);
  assert.equal(clampPage(-1, 3), 0);
  assert.equal(clampPage(1, 3), 1);
  assert.equal(clampPage(4, 0), 0);
  assert.equal(clampPage(Number.NaN, 3), 0);
});

test('pageSummary', () => {
  assert.equal(pageSummary(0, 10, 0), 'Belum ada admin');
  assert.equal(pageSummary(0, 10, 7), '1–7 dari 7');
  assert.equal(pageSummary(1, 10, 23), '11–20 dari 23');
  assert.equal(pageSummary(2, 10, 23), '21–23 dari 23');
  assert.equal(pageSummary(2, 10, 21), '21 dari 21');
  assert.equal(pageSummary(9, 10, 23), '0 dari 23');
});

test('query URL (mulai 1) <-> indeks API (mulai 0)', () => {
  assert.equal(pageIndexFromQuery(undefined), 0);
  assert.equal(pageIndexFromQuery('1'), 0);
  assert.equal(pageIndexFromQuery('3'), 2);
  assert.equal(pageIndexFromQuery('0'), 0);
  assert.equal(pageIndexFromQuery('-2'), 0);
  assert.equal(pageIndexFromQuery('abc'), 0);
  assert.equal(pageIndexFromQuery('2x'), 0);
  assert.equal(pageIndexFromQuery(['4', '5']), 3);
  assert.equal(pageIndexFromQuery(null), 0);
  assert.deepEqual(pageQueryFromIndex(0), {});
  assert.deepEqual(pageQueryFromIndex(2), { page: '3' });
  for (const i of [0, 1, 7]) assert.equal(pageIndexFromQuery(pageQueryFromIndex(i).page), i);
});

test('formatAdminDate', () => {
  assert.equal(formatAdminDate(null), '-');
  assert.equal(formatAdminDate('xx'), '-');
  assert.match(formatAdminDate('2030-06-01T10:30:00'), /2030/);
});

// filepath: /frontend/tests/adminUser.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  MAX_QUERY_LENGTH, SEARCH_DEBOUNCE_MS, USER_TIERS, buildListParams, filtersFromQuery, hasActiveFilters,
  isTierChange, isUserTier, normalizeQuery, queryFromFilters, tierChangeMessage, tierChangedNotice, tierLabel,
  userStatus,
} from '../src/modules/admin/utils/adminUser.ts';
import { adminDisplayName, pageIndexFromQuery, pageQueryFromIndex } from '../src/modules/admin/utils/adminList.ts';

test('tiga tier berurutan Free, VIP Monthly, VIP Yearly dan ADMIN tidak termasuk', () => {
  assert.deepEqual(USER_TIERS.map((t) => t.value), ['FREE', 'VIP_MONTHLY', 'VIP_YEARLY']);
  assert.deepEqual(USER_TIERS.map((t) => t.label), ['Free', 'VIP Monthly', 'VIP Yearly']);
  assert.equal(isUserTier('ADMIN'), false);
  assert.equal(isUserTier('free'), false);
  assert.equal(isUserTier('VIP_YEARLY'), true);
  assert.equal(isUserTier(undefined), false);
});

test('tierLabel: kode dikenal -> label, tidak dikenal -> kodenya', () => {
  assert.equal(tierLabel('VIP_MONTHLY'), 'VIP Monthly');
  assert.equal(tierLabel('LAIN'), 'LAIN');
});

test('normalizeQuery: trim dan potong di batas backend (100)', () => {
  assert.equal(MAX_QUERY_LENGTH, 100);
  assert.equal(normalizeQuery('  budi  '), 'budi');
  assert.equal(normalizeQuery('   '), '');
  assert.equal(normalizeQuery('a'.repeat(150)).length, 100);
  assert.equal(normalizeQuery('a'.repeat(99) + ' bbb').length, 99); // spasi di ujung hasil potong dibuang
  assert.ok(SEARCH_DEBOUNCE_MS >= 300);
});

test('filtersFromQuery: tier tidak dikenal diabaikan, array diambil elemen pertama', () => {
  assert.deepEqual(filtersFromQuery({}), { q: '', tier: '' });
  assert.deepEqual(filtersFromQuery({ q: ' Budi ', tier: 'FREE' }), { q: 'Budi', tier: 'FREE' });
  assert.deepEqual(filtersFromQuery({ tier: 'ADMIN' }), { q: '', tier: '' });
  assert.deepEqual(filtersFromQuery({ tier: 'free' }), { q: '', tier: '' });
  assert.deepEqual(filtersFromQuery({ q: ['a', 'b'], tier: ['VIP_YEARLY', 'FREE'] }), { q: 'a', tier: 'VIP_YEARLY' });
  assert.deepEqual(filtersFromQuery({ q: 5, tier: null }), { q: '', tier: '' });
});

test('queryFromFilters: parameter kosong dan halaman pertama tidak ditulis; nomor halaman mulai 1', () => {
  assert.deepEqual(queryFromFilters({ q: '', tier: '' }, 0), {});
  assert.deepEqual(queryFromFilters({ q: '  ', tier: '' }, 0), {});
  assert.deepEqual(queryFromFilters({ q: 'budi', tier: '' }, 0), { q: 'budi' });
  assert.deepEqual(queryFromFilters({ q: '', tier: 'FREE' }, 0), { tier: 'FREE' });
  assert.deepEqual(queryFromFilters({ q: 'budi', tier: 'VIP_MONTHLY' }, 2), { q: 'budi', tier: 'VIP_MONTHLY', page: '3' });
  assert.deepEqual(queryFromFilters({ q: '', tier: '' }, 1), { page: '2' });
});

test('round-trip filter + halaman lewat URL', () => {
  const cases: Array<[{ q: string; tier: '' | 'FREE' | 'VIP_MONTHLY' | 'VIP_YEARLY' }, number]> = [
    [{ q: '', tier: '' }, 0], [{ q: 'budi santoso', tier: 'FREE' }, 3], [{ q: 'a@b.com', tier: 'VIP_YEARLY' }, 0],
  ];
  for (const [filters, index] of cases) {
    const query = queryFromFilters(filters, index);
    assert.deepEqual(filtersFromQuery(query), filters);
    assert.equal(pageIndexFromQuery(query.page), index);
  }
});

test('hasActiveFilters', () => {
  assert.equal(hasActiveFilters({ q: '', tier: '' }), false);
  assert.equal(hasActiveFilters({ q: '   ', tier: '' }), false);
  assert.equal(hasActiveFilters({ q: 'x', tier: '' }), true);
  assert.equal(hasActiveFilters({ q: '', tier: 'FREE' }), true);
});

test('buildListParams: q dan tier hanya dikirim bila terisi', () => {
  assert.deepEqual(buildListParams(0, 10, { q: '', tier: '' }), { page: 0, size: 10 });
  assert.deepEqual(buildListParams(0, 10, { q: '   ', tier: '' }), { page: 0, size: 10 });
  assert.deepEqual(buildListParams(2, 10, { q: ' budi ', tier: '' }), { page: 2, size: 10, q: 'budi' });
  assert.deepEqual(buildListParams(0, 10, { q: '', tier: 'FREE' }), { page: 0, size: 10, tier: 'FREE' });
  assert.deepEqual(buildListParams(1, 10, { q: 'x', tier: 'VIP_YEARLY' }), { page: 1, size: 10, q: 'x', tier: 'VIP_YEARLY' });
});

test('isTierChange: hanya tier valid yang berbeda dari sekarang', () => {
  const item = { userType: 'FREE' as const };
  assert.equal(isTierChange(item, 'VIP_MONTHLY'), true);
  assert.equal(isTierChange(item, 'FREE'), false);
  assert.equal(isTierChange(item, 'ADMIN'), false);
  assert.equal(isTierChange(item, ''), false);
});

test('tierChangeMessage menyebut nama, email, tier lama dan baru', () => {
  const text = tierChangeMessage({ name: 'Budi', email: 'b@x.id', userType: 'FREE' }, 'VIP_YEARLY');
  assert.match(text, /Budi \(b@x\.id\)/);
  assert.match(text, /dari Free ke VIP Yearly/);
  // nama kosong -> jatuh ke email
  assert.match(tierChangeMessage({ name: null, email: 'b@x.id', userType: 'FREE' }, 'VIP_MONTHLY'), /^b@x\.id \(b@x\.id\)/);
});

test('tierChangedNotice: menjelaskan baris yang hilang karena filter tier aktif', () => {
  const who = { name: 'Budi', email: 'b@x.id' };
  assert.equal(tierChangedNotice(who, 'VIP_MONTHLY', ''), 'Budi sekarang VIP Monthly.');
  assert.equal(tierChangedNotice(who, 'VIP_MONTHLY', 'VIP_MONTHLY'), 'Budi sekarang VIP Monthly.');
  assert.match(tierChangedNotice(who, 'VIP_MONTHLY', 'FREE'), /tidak lagi tampil karena filter Free aktif/);
});

test('userStatus: nonaktif mengalahkan belum verifikasi', () => {
  assert.deepEqual(userStatus({ active: true, verified: true }), { text: 'Aktif', tone: 'success' });
  assert.deepEqual(userStatus({ active: true, verified: false }), { text: 'Belum verifikasi', tone: 'warning' });
  assert.deepEqual(userStatus({ active: false, verified: true }), { text: 'Nonaktif', tone: 'muted' });
  assert.deepEqual(userStatus({ active: false, verified: false }), { text: 'Nonaktif', tone: 'muted' });
});

test('service mengirim params dari buildListParams ke GET /admin/users dan PATCH tier', () => {
  const source = readFileSync(new URL('../src/modules/admin/services/adminUser.service.ts', import.meta.url), 'utf8');
  assert.match(source, /apiClient\.get<UserListPage>\('\/admin\/users',\s*\{\s*params:\s*buildListParams\(page,\s*size,\s*filters\)/);
  assert.match(source, /apiClient\.patch<UserListItem>\(`\/admin\/users\/\$\{encodeURIComponent\(id\)\}\/tier`,\s*body\)/);
  assert.match(source, /const body: UpdateUserTierRequest = \{ userType \}/);
});

test('view menunda pencarian (debounce), mereset halaman saat filter berubah, dan mengembalikan select sebelum konfirmasi', () => {
  const source = readFileSync(new URL('../src/modules/admin/views/AdminUserListView.vue', import.meta.url), 'utf8');
  assert.match(source, /setTimeout\(applySearchNow,\s*SEARCH_DEBOUNCE_MS\)/);
  assert.match(source, /queryFromFilters\(next,\s*0\)/); // filter berubah -> halaman 1
  assert.match(source, /select\.value = item\.userType/);
  assert.match(source, /router\[mode\]/);
});

test('salinan lokal adminDisplayName/pageQueryFromIndex tetap sama dengan adminList.ts', () => {
  for (const index of [0, 1, 2, 9]) {
    assert.deepEqual(queryFromFilters({ q: '', tier: '' }, index), pageQueryFromIndex(index));
  }
  for (const [name, email] of [['Budi', 'b@x.id'], [null, 'b@x.id'], ['  ', 'b@x.id'], [' Sari ', 's@x.id']] as const) {
    const expected = adminDisplayName({ name, email });
    assert.match(tierChangeMessage({ name, email, userType: 'FREE' }, 'VIP_MONTHLY'), new RegExp(`^${expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} \\(`));
  }
});

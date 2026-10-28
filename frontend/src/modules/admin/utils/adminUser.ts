// filepath: /frontend/src/modules/admin/utils/adminUser.ts
import type { UserListFilters, UserListItem, UserTier } from '../types/admin-user.types';
import type { StatusTone } from './adminList';

// Logika murni menu User (tanpa .vue) agar bisa diuji dengan node:test. Modul ini sengaja TIDAK mengimpor
// nilai dari adminList.ts: Node (tanpa bundler) tidak menambahkan ekstensi pada import relatif. Dua fungsi kecil di
// bawah menduplikasi adminList.ts (adminDisplayName, pageQueryFromIndex); adminUser.test.ts menjaga keduanya tetap sama.

/** Jeda (ms) setelah berhenti mengetik sebelum pencarian dikirim. */
export const SEARCH_DEBOUNCE_MS = 400;
/** Sama dengan batas backend; lebih dari ini ditolak 400. */
export const MAX_QUERY_LENGTH = 100;

export interface TierOption {
  value: UserTier;
  label: string;
}

// Urutan dan label sama dengan seeder backend. GET /api/user-types tidak bisa dipakai admin (hanya ROLE_USER).
export const USER_TIERS: readonly TierOption[] = [
  { value: 'FREE', label: 'Free' },
  { value: 'VIP_MONTHLY', label: 'VIP Monthly' },
  { value: 'VIP_YEARLY', label: 'VIP Yearly' },
];

export function isUserTier(value: unknown): value is UserTier {
  return USER_TIERS.some((t) => t.value === value);
}

export function tierLabel(tier: string): string {
  return USER_TIERS.find((t) => t.value === tier)?.label ?? tier;
}

// ---------------------------------------------------------------- filter <-> URL

/** Kata kunci siap kirim: spasi di ujung dibuang dan dipotong ke batas backend. */
export function normalizeQuery(raw: string): string {
  return raw.trim().slice(0, MAX_QUERY_LENGTH).trim();
}

function adminDisplayName(item: Pick<UserListItem, 'name' | 'email'>): string {
  const name = item.name?.trim();
  return name ? name : item.email;
}

function pageQueryFromIndex(index: number): { page?: string } {
  return index > 0 ? { page: String(index + 1) } : {};
}

function firstString(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' ? raw : '';
}

/** Filter dari query URL. Nilai tier yang tidak dikenal diabaikan (tidak dikirim ke backend). */
export function filtersFromQuery(query: Record<string, unknown>): UserListFilters {
  const tier = firstString(query.tier);
  return { q: normalizeQuery(firstString(query.q)), tier: isUserTier(tier) ? tier : '' };
}

/** Query URL dari filter + indeks halaman (mulai 0). Parameter kosong dan halaman pertama tidak ditulis. */
export function queryFromFilters(filters: UserListFilters, pageIndex: number): Record<string, string> {
  const out: Record<string, string> = {};
  const q = normalizeQuery(filters.q);
  if (q) out.q = q;
  if (filters.tier) out.tier = filters.tier;
  return { ...out, ...pageQueryFromIndex(pageIndex) };
}

export function hasActiveFilters(filters: UserListFilters): boolean {
  return normalizeQuery(filters.q) !== '' || filters.tier !== '';
}

/** Parameter GET /admin/users. q dan tier hanya dikirim bila terisi. */
export function buildListParams(
  page: number,
  size: number,
  filters: UserListFilters
): { page: number; size: number; q?: string; tier?: UserTier } {
  const params: { page: number; size: number; q?: string; tier?: UserTier } = { page, size };
  const q = normalizeQuery(filters.q);
  if (q) params.q = q;
  if (filters.tier) params.tier = filters.tier;
  return params;
}

// ---------------------------------------------------------------- baris & ubah tier

export function isTierChange(item: Pick<UserListItem, 'userType'>, next: string): next is UserTier {
  return isUserTier(next) && next !== item.userType;
}

export function tierChangeMessage(item: Pick<UserListItem, 'name' | 'email' | 'userType'>, next: UserTier): string {
  return (
    `${adminDisplayName(item)} (${item.email}) akan diubah dari ${tierLabel(item.userType)} ke ${tierLabel(next)}. ` +
    'Perubahan berlaku langsung, tanpa user perlu login ulang.'
  );
}

/** Pesan sukses; bila baris tidak lagi cocok dengan filter tier aktif, beri tahu ke mana barisnya pergi. */
export function tierChangedNotice(
  item: Pick<UserListItem, 'name' | 'email'>,
  next: UserTier,
  activeTier: UserListFilters['tier']
): string {
  const base = `${adminDisplayName(item)} sekarang ${tierLabel(next)}.`;
  return activeTier && activeTier !== next
    ? `${base} User ini tidak lagi tampil karena filter ${tierLabel(activeTier)} aktif.`
    : base;
}

export interface UserStatus {
  text: string;
  tone: StatusTone;
}

/** Nonaktif mengalahkan yang lain, lalu belum verifikasi email, selain itu aktif. */
export function userStatus(item: Pick<UserListItem, 'active' | 'verified'>): UserStatus {
  if (!item.active) return { text: 'Nonaktif', tone: 'muted' };
  if (!item.verified) return { text: 'Belum verifikasi', tone: 'warning' };
  return { text: 'Aktif', tone: 'success' };
}

// filepath: /frontend/src/modules/admin/utils/adminList.ts
import type { AdminListItem } from '../types/admin-auth.types';

// ---------------------------------------------------------------- aksi per baris

export type AdminRowAction = 'deactivate' | 'activate' | 'resend';

/**
 * Aksi yang boleh muncul di satu baris (backend tetap menjadi penjaga akhir):
 * - baris sendiri: tidak ada aksi (tidak boleh menonaktifkan diri sendiri);
 * - ACTIVE: nonaktifkan;
 * - PENDING: nonaktifkan; plus kirim ulang undangan bila statusnya menunggu undangan
 *   (admin pertama yang belum verifikasi email tidak punya undangan, jadi tidak bisa dikirim ulang);
 * - INACTIVE: aktifkan kembali.
 */
export function rowActions(item: Pick<AdminListItem, 'status' | 'self' | 'invitationExpiresAt'>): AdminRowAction[] {
  if (item.self) return [];
  switch (item.status) {
    case 'ACTIVE':
      return ['deactivate'];
    case 'PENDING':
      return item.invitationExpiresAt !== null ? ['resend', 'deactivate'] : ['deactivate'];
    case 'INACTIVE':
      return ['activate'];
    default:
      return [];
  }
}

// ---------------------------------------------------------------- tampilan nama & kirim ulang undangan

/** Nama untuk ditampilkan: nama bila ada, kalau tidak emailnya. */
export function adminDisplayName(item: Pick<AdminListItem, 'name' | 'email'>): string {
  const name = item.name?.trim();
  return name ? name : item.email;
}

/** Body kirim-ulang undangan (POST /admin/admins): nama wajib diisi di backend, jadi jatuh ke email bila kosong. */
export function resendInviteRequest(item: Pick<AdminListItem, 'name' | 'email'>): { name: string; email: string } {
  return { name: adminDisplayName(item), email: item.email };
}

// ---------------------------------------------------------------- label status

export type StatusTone = 'success' | 'warning' | 'danger' | 'muted';

export interface StatusLabel {
  text: string;
  tone: StatusTone;
}

export function isInvitationExpired(invitationExpiresAt: string | null, nowMs: number): boolean {
  if (invitationExpiresAt === null) return false;
  const expires = Date.parse(invitationExpiresAt);
  return Number.isFinite(expires) && expires <= nowMs;
}

export function statusLabel(
  item: Pick<AdminListItem, 'status' | 'invitationExpiresAt'>,
  nowMs: number
): StatusLabel {
  switch (item.status) {
    case 'ACTIVE':
      return { text: 'Aktif', tone: 'success' };
    case 'INACTIVE':
      return { text: 'Nonaktif', tone: 'muted' };
    case 'PENDING':
      if (item.invitationExpiresAt === null) return { text: 'Menunggu verifikasi email', tone: 'warning' };
      return isInvitationExpired(item.invitationExpiresAt, nowMs)
        ? { text: 'Undangan kedaluwarsa', tone: 'danger' }
        : { text: 'Menunggu undangan', tone: 'warning' };
    default:
      return { text: String(item.status), tone: 'muted' };
  }
}

// ---------------------------------------------------------------- paginasi

export const ADMIN_PAGE_SIZE = 10;

/** Halaman (indeks mulai 0) dipaksa ke 0..(totalPages-1); totalPages 0 -> 0; nilai bukan angka -> 0. */
export function clampPage(page: number, totalPages: number): number {
  if (!Number.isFinite(page) || !Number.isFinite(totalPages)) return 0;
  const last = Math.max(Math.floor(totalPages) - 1, 0);
  return Math.min(Math.max(Math.floor(page), 0), last);
}

/** "11–20 dari 23". Kosong -> "Belum ada admin". `page` mulai dari 0. */
export function pageSummary(page: number, size: number, totalItems: number): string {
  if (totalItems <= 0 || size <= 0) return 'Belum ada admin';
  const from = page * size + 1;
  if (from > totalItems) return `0 dari ${totalItems}`;
  const to = Math.min((page + 1) * size, totalItems);
  return from === to ? `${from} dari ${totalItems}` : `${from}–${to} dari ${totalItems}`;
}

/**
 * Query URL memakai nomor halaman MULAI DARI 1 (?page=2 = halaman kedua), API memakai indeks mulai 0.
 * Nilai tidak valid / array / kosong -> halaman pertama (indeks 0).
 */
export function pageIndexFromQuery(value: unknown): number {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return 0;
  return Math.max(Number.parseInt(raw, 10) - 1, 0);
}

/** Kebalikan pageIndexFromQuery: halaman pertama tidak perlu parameter di URL. */
export function pageQueryFromIndex(index: number): { page?: string } {
  return index > 0 ? { page: String(index + 1) } : {};
}

// ---------------------------------------------------------------- tampilan tanggal

/** Tanggal & jam dalam format Indonesia; '-' bila kosong/tidak valid. (Waktu server tanpa zona dibaca sebagai waktu lokal.) */
export function formatAdminDate(iso: string | null): string {
  if (!iso) return '-';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('id-ID', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

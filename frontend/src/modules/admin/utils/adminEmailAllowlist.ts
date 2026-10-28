// filepath: /frontend/src/modules/admin/utils/adminEmailAllowlist.ts
// Fungsi murni (bebas .vue agar bisa diuji). Aturan format SAMA dengan backend EmailAllowlistServiceImpl;
// backend tetap penentu akhir -- ini hanya untuk umpan balik cepat.
import type { AllowedEmailRuleType } from '../types/admin-email-allowlist.types';

const EMAIL = /^[a-z0-9._%+-]+@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;
const DOMAIN = /^@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;

export const RULE_TYPE_OPTIONS: readonly { id: AllowedEmailRuleType; label: string; placeholder: string }[] = [
  { id: 'EMAIL', label: 'Email tertentu', placeholder: 'budi@perusahaan.com' },
  { id: 'DOMAIN', label: 'Seluruh domain', placeholder: '@perusahaan.com' },
];

/** Sama dengan backend: trim + huruf kecil; domain tanpa "@" diberi "@" di depan. */
export function normalizeRuleValue(type: AllowedEmailRuleType, raw: string): string {
  const value = raw.trim().toLowerCase();
  if (type === 'DOMAIN' && value !== '' && !value.includes('@')) return `@${value}`;
  return value;
}

/** Pesan error, atau '' bila valid. */
export function validateRuleValue(type: AllowedEmailRuleType, raw: string): string {
  const value = normalizeRuleValue(type, raw);
  if (value === '') return 'Nilai wajib diisi';
  if (value.length > 255) return 'Nilai maksimal 255 karakter';
  if (type === 'EMAIL') return EMAIL.test(value) ? '' : 'Format email tidak valid (contoh: budi@perusahaan.com)';
  return DOMAIN.test(value) ? '' : 'Format domain tidak valid (contoh: @perusahaan.com)';
}

export function ruleTypeLabel(type: AllowedEmailRuleType): string {
  return type === 'EMAIL' ? 'Email' : 'Domain';
}

/** Aktif tetapi daftar kosong = backend membiarkan semua mendaftar (fail-open); admin perlu diingatkan. */
export function isEnabledButEmpty(enabled: boolean, ruleCount: number): boolean {
  return enabled && ruleCount === 0;
}

// filepath: /frontend/src/modules/admin/utils/adminAiLimits.ts
import type {
  AiLimits,
  AiLimitsUpdateRequest,
  AiLimitTier,
  AiTierLimits,
} from '../types/admin-ai-limits.types';

// Logika murni menu "Batas AI". Aturan sebenarnya (rentang, tanpa batas) divalidasi backend; rentang di sini hanya
// mempercepat umpan balik di form dan HARUS sama dengan AiLimitKey.java.

export const AI_LIMIT_TIERS: readonly { id: AiLimitTier; label: string }[] = [
  { id: 'FREE', label: 'Free' },
  { id: 'VIP_MONTHLY', label: 'VIP Monthly' },
  { id: 'VIP_YEARLY', label: 'VIP Yearly' },
];

export const GLOBAL_SCOPE = 'GLOBAL';

export type AiLimitGroup = 'automation' | 'testcase' | 'global';

export interface AiLimitField {
  /** Sama dengan nama enum AiLimitKey di backend. */
  id: string;
  group: AiLimitGroup;
  /** Nama properti di DTO (mis. 'dailyLimit'). */
  prop: string;
  label: string;
  hint: string;
  min: number;
  max: number;
  /** Nilai 0 berarti TANPA BATAS (butuh konfirmasi). */
  unlimitedAtZero: boolean;
}

export const AI_LIMIT_FIELDS: readonly AiLimitField[] = [
  { id: 'AUTOMATION_DAILY_LIMIT', group: 'automation', prop: 'dailyLimit', label: 'Generate per hari (per user)', hint: '0 = tanpa batas', min: 0, max: 10_000, unlimitedAtZero: true },
  { id: 'AUTOMATION_MAX_TEST_CASES', group: 'automation', prop: 'maxTestCases', label: 'Maks. test case per generate', hint: 'jumlah', min: 1, max: 100, unlimitedAtZero: false },
  { id: 'AUTOMATION_MAX_OUTPUT_TOKENS', group: 'automation', prop: 'maxOutputTokens', label: 'Maks. token keluaran', hint: 'token (memengaruhi biaya)', min: 256, max: 65_536, unlimitedAtZero: false },
  { id: 'AUTOMATION_TIMEOUT_SECONDS', group: 'automation', prop: 'timeoutSeconds', label: 'Timeout', hint: 'detik', min: 10, max: 600, unlimitedAtZero: false },
  { id: 'TESTCASE_DAILY_LIMIT', group: 'testcase', prop: 'dailyLimit', label: 'Generate per hari (per user)', hint: '0 = tanpa batas', min: 0, max: 10_000, unlimitedAtZero: true },
  { id: 'TESTCASE_MAX_DRAFTS', group: 'testcase', prop: 'maxDrafts', label: 'Maks. draft per generate', hint: 'jumlah', min: 1, max: 50, unlimitedAtZero: false },
  { id: 'TESTCASE_MAX_OUTPUT_TOKENS', group: 'testcase', prop: 'maxOutputTokens', label: 'Maks. token keluaran', hint: 'token (memengaruhi biaya)', min: 256, max: 65_536, unlimitedAtZero: false },
  { id: 'TESTCASE_TIMEOUT_SECONDS', group: 'testcase', prop: 'timeoutSeconds', label: 'Timeout', hint: 'detik', min: 10, max: 600, unlimitedAtZero: false },
  { id: 'GLOBAL_DAILY_LIMIT', group: 'global', prop: 'dailyLimit', label: 'Generate per hari (seluruh sistem)', hint: 'semua fitur AI; 0 = tanpa batas', min: 0, max: 1_000_000, unlimitedAtZero: true },
  { id: 'QUOTA_CIRCUIT_MINUTES', group: 'global', prop: 'quotaCircuitMinutes', label: 'Jeda setelah kuota provider habis', hint: 'menit', min: 1, max: 1_440, unlimitedAtZero: false },
];

export const TIER_FIELD_GROUPS: readonly { group: 'automation' | 'testcase'; title: string }[] = [
  { group: 'automation', title: 'Generate code automation' },
  { group: 'testcase', title: 'Generate test case' },
];

export function fieldsOf(group: AiLimitGroup): AiLimitField[] {
  return AI_LIMIT_FIELDS.filter((f) => f.group === group);
}

/** Kunci penyimpanan/source/form: "FREE.AUTOMATION_DAILY_LIMIT", "GLOBAL.GLOBAL_DAILY_LIMIT". */
export function limitKey(scope: string, fieldId: string): string {
  return `${scope}.${fieldId}`;
}

function fieldOf(fieldId: string): AiLimitField {
  const field = AI_LIMIT_FIELDS.find((f) => f.id === fieldId);
  if (!field) throw new Error(`Batas AI tidak dikenal: ${fieldId}`);
  return field;
}

type Values = Pick<AiLimits, 'global' | 'tiers'>;

/** Nilai pada (scope, fieldId) dari objek nilai API. */
export function readValue(values: Values, scope: string, fieldId: string): number {
  const field = fieldOf(fieldId);
  if (field.group === 'global') {
    return (values.global as unknown as Record<string, number>)[field.prop];
  }
  const tier = values.tiers[scope as AiLimitTier] as AiTierLimits;
  return (tier[field.group] as unknown as Record<string, number>)[field.prop];
}

/** Seluruh kunci form: 2 global + 8 per tier x 3 tier. */
export function allLimitKeys(): string[] {
  const keys: string[] = fieldsOf('global').map((f) => limitKey(GLOBAL_SCOPE, f.id));
  for (const tier of AI_LIMIT_TIERS) {
    for (const f of AI_LIMIT_FIELDS.filter((x) => x.group !== 'global')) keys.push(limitKey(tier.id, f.id));
  }
  return keys;
}

function splitKey(key: string): { scope: string; fieldId: string } {
  const dot = key.indexOf('.');
  return { scope: key.slice(0, dot), fieldId: key.slice(dot + 1) };
}

/** Form = teks input per kunci (string, supaya kolom boleh kosong sementara). */
export type AiLimitsForm = Record<string, string>;

export function formFromLimits(limits: Values): AiLimitsForm {
  const form: AiLimitsForm = {};
  for (const key of allLimitKeys()) {
    const { scope, fieldId } = splitKey(key);
    form[key] = String(readValue(limits, scope, fieldId));
  }
  return form;
}

/** Bilangan bulat >= 0 atau null (kosong, desimal, huruf, minus, notasi ilmiah ditolak). */
export function parseLimitValue(text: string): number | null {
  const t = text.trim();
  if (!/^\d{1,9}$/.test(t)) return null;
  return Number(t);
}

/** Pesan error per kunci form; objek kosong = form valid. */
export function validateForm(form: AiLimitsForm): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const key of allLimitKeys()) {
    const { fieldId } = splitKey(key);
    const field = fieldOf(fieldId);
    const value = parseLimitValue(form[key] ?? '');
    if (value === null) errors[key] = 'Isi dengan bilangan bulat (tanpa minus atau desimal).';
    else if (value < field.min || value > field.max) errors[key] = `Harus antara ${field.min} dan ${field.max}.`;
  }
  return errors;
}

/** Kunci yang nilai form-nya (valid) berbeda dari nilai efektif sekarang. */
export function changedKeys(form: AiLimitsForm, limits: Values): string[] {
  return allLimitKeys().filter((key) => {
    const value = parseLimitValue(form[key] ?? '');
    if (value === null) return false;
    const { scope, fieldId } = splitKey(key);
    return value !== readValue(limits, scope, fieldId);
  });
}

export function isFormDirty(form: AiLimitsForm, limits: Values): boolean {
  return changedKeys(form, limits).length > 0 || allLimitKeys().some((k) => parseLimitValue(form[k] ?? '') === null);
}

/** Kunci batas harian yang BARU diubah menjadi 0 (tanpa batas): wajib dikonfirmasi admin. */
export function unlimitedChanges(form: AiLimitsForm, limits: Values): string[] {
  return changedKeys(form, limits).filter((key) => {
    const { fieldId } = splitKey(key);
    return fieldOf(fieldId).unlimitedAtZero && parseLimitValue(form[key]) === 0;
  });
}

/** Body PUT dari form yang SUDAH valid (validateForm kosong). */
export function buildUpdateRequest(form: AiLimitsForm, confirmUnlimited: boolean): AiLimitsUpdateRequest {
  const errors = validateForm(form);
  if (Object.keys(errors).length > 0) throw new Error('Form batas AI belum valid');
  const num = (scope: string, fieldId: string): number => parseLimitValue(form[limitKey(scope, fieldId)]) as number;

  const tiers = {} as Record<AiLimitTier, AiTierLimits>;
  for (const tier of AI_LIMIT_TIERS) {
    tiers[tier.id] = {
      automation: {
        dailyLimit: num(tier.id, 'AUTOMATION_DAILY_LIMIT'),
        maxTestCases: num(tier.id, 'AUTOMATION_MAX_TEST_CASES'),
        maxOutputTokens: num(tier.id, 'AUTOMATION_MAX_OUTPUT_TOKENS'),
        timeoutSeconds: num(tier.id, 'AUTOMATION_TIMEOUT_SECONDS'),
      },
      testcase: {
        dailyLimit: num(tier.id, 'TESTCASE_DAILY_LIMIT'),
        maxDrafts: num(tier.id, 'TESTCASE_MAX_DRAFTS'),
        maxOutputTokens: num(tier.id, 'TESTCASE_MAX_OUTPUT_TOKENS'),
        timeoutSeconds: num(tier.id, 'TESTCASE_TIMEOUT_SECONDS'),
      },
    };
  }
  return {
    global: {
      dailyLimit: num(GLOBAL_SCOPE, 'GLOBAL_DAILY_LIMIT'),
      quotaCircuitMinutes: num(GLOBAL_SCOPE, 'QUOTA_CIRCUIT_MINUTES'),
    },
    tiers,
    confirmUnlimited,
  };
}

/** true bila kunci ini ditimpa database (bukan nilai bawaan konfigurasi). */
export function isOverridden(limits: Pick<AiLimits, 'source'>, key: string): boolean {
  return limits.source[key] === 'DATABASE';
}

export function overriddenKeys(limits: Pick<AiLimits, 'source'>): string[] {
  return Object.keys(limits.source).filter((k) => limits.source[k] === 'DATABASE');
}

/** Nilai bawaan konfigurasi untuk kunci ini (untuk petunjuk "bawaan: N"). */
export function defaultValue(limits: Pick<AiLimits, 'defaults'>, key: string): number {
  const { scope, fieldId } = splitKey(key);
  return readValue(limits.defaults, scope, fieldId);
}

/** Teks tampilan nilai: batas harian 0 = "Tanpa batas". */
export function displayValue(fieldId: string, value: number): string {
  const field = fieldOf(fieldId);
  if (field.unlimitedAtZero && value === 0) return 'Tanpa batas';
  return value.toLocaleString('id-ID');
}

/** Nama yang bisa dibaca untuk dialog/pesan: "VIP Yearly: Generate per hari (per user)". */
export function limitLabel(key: string): string {
  const { scope, fieldId } = splitKey(key);
  const field = fieldOf(fieldId);
  if (scope === GLOBAL_SCOPE) return field.label;
  const tier = AI_LIMIT_TIERS.find((t) => t.id === scope)?.label ?? scope;
  const group = field.group === 'automation' ? 'Automation' : 'Test case';
  return `${tier}, ${group}: ${field.label}`;
}

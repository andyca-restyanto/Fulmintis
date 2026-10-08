// frontend/src/shared/utils/aiErrors.ts
// Memetakan error backend fitur AI (Automation, Generate Test Case) ke pesan yang ditampilkan. Dipakai bersama oleh
// semua fitur AI supaya user melihat SATU suara yang sama dan tidak ada modul yang saling mengimpor.
//
// ATURAN PENTING (requirement #6): kegagalan di sisi penyedia AI -- termasuk kuota/saldo habis -- HANYA boleh
// tampil sebagai pesan UMUM. Pesan itu ditulis di SINI sebagai konstanta (bukan dibaca dari server) supaya tidak
// ada jalur yang bisa membocorkan kata "kuota"/"billing"/nama provider ke layar. Batas harian milik user
// (AI_PLAN_LIMIT_REACHED) berbeda: bisa ditindaklanjuti user, jadi pesannya spesifik dan dibaca dari server.

export const GENERIC_AI_UNAVAILABLE_MESSAGE = 'Layanan AI sedang tidak tersedia. Silakan coba lagi nanti.';

export type AiErrorKind =
  | 'ai-unavailable' // kegagalan sisi AI (pesan umum)
  | 'plan-limit' // batas harian user tercapai (pesan spesifik)
  | 'setup-required' // (Automation) struktur project belum diatur
  | 'in-progress' // sudah ada job berjalan
  | 'rate-limit' // rate limit umum (429 tanpa errorCode)
  | 'validation' // permintaan tidak valid / konflik (pesan dari server)
  | 'generic';

export interface DescribedAiError {
  kind: AiErrorKind;
  message: string;
  errorCode: string | null;
}

function readBody(data: unknown): { message: string | null; errorCode: string | null } {
  if (data && typeof data === 'object') {
    const body = data as { message?: unknown; errorCode?: unknown };
    return {
      message: typeof body.message === 'string' && body.message.trim() !== '' ? body.message : null,
      errorCode: typeof body.errorCode === 'string' ? body.errorCode : null,
    };
  }
  return { message: null, errorCode: null };
}

// Kode "permintaan tidak valid / konflik": pesan dari server aman ditampilkan apa adanya.
const VALIDATION_CODES = new Set([
  // Automation
  'TOO_MANY_TEST_CASES',
  'INVALID_TEST_CASE_SELECTION',
  'UNSUPPORTED_COMBINATION',
  'SETUP_CONFLICT',
  'GENERATION_NOT_READY',
  'GENERATION_NOT_FOUND',
  // Generate Test Case
  'INVALID_REQUEST',
  'REQUIREMENT_TOO_LONG',
  'TOO_MANY_DRAFTS',
  'NO_DRAFTS_SELECTED',
  'INVALID_DRAFTS',
  'GENERATION_ALREADY_COMMITTED',
  'DRAFT_EXPIRED',
]);

/** Dari respons HTTP gagal (status + body) -> pesan tampilan. */
export function describeAiError(status: number | undefined, data: unknown, fallback: string): DescribedAiError {
  const { message, errorCode } = readBody(data);

  if (errorCode === 'AI_UNAVAILABLE' || (status === 503 && errorCode === null)) {
    return { kind: 'ai-unavailable', message: GENERIC_AI_UNAVAILABLE_MESSAGE, errorCode: 'AI_UNAVAILABLE' };
  }
  if (errorCode === 'AI_PLAN_LIMIT_REACHED') {
    return { kind: 'plan-limit', message: message ?? 'Batas generate harian Anda tercapai. Coba lagi besok.', errorCode };
  }
  if (errorCode === 'AUTOMATION_SETUP_REQUIRED') {
    return { kind: 'setup-required', message: message ?? 'Atur struktur automation project ini terlebih dahulu.', errorCode };
  }
  if (errorCode === 'GENERATION_IN_PROGRESS') {
    return { kind: 'in-progress', message: message ?? 'Masih ada proses generate Anda yang berjalan.', errorCode };
  }
  if (errorCode !== null && VALIDATION_CODES.has(errorCode)) {
    return { kind: 'validation', message: message ?? fallback, errorCode };
  }
  if (status === 429) {
    return { kind: 'rate-limit', message: message ?? 'Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi.', errorCode };
  }
  if (status === 400 || status === 409) {
    return { kind: 'validation', message: message ?? fallback, errorCode };
  }
  return { kind: 'generic', message: message ?? fallback, errorCode };
}

/**
 * Pesan utk job yang berstatus FAILED. errorMessage dari server sudah aman ditampilkan, KECUALI kita tetap
 * memaksa pesan umum utk AI_UNAVAILABLE (lihat catatan di atas). Kode lain (AI_INVALID_OUTPUT, GENERATION_FAILED,
 * GENERATION_INTERRUPTED, SERVER_BUSY) memakai pesan server.
 */
export function describeGenerationFailure(errorCode: string | null, errorMessage: string | null): string {
  if (errorCode === 'AI_UNAVAILABLE') {
    return GENERIC_AI_UNAVAILABLE_MESSAGE;
  }
  return errorMessage && errorMessage.trim() !== '' ? errorMessage : 'Generate gagal. Silakan coba lagi.';
}

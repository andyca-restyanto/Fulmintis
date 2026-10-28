// frontend/src/modules/testcase/utils/testCaseAiGate.ts
// Logika murni (tanpa Vue) utk tombol & modal "Generate with AI": kapan tombol boleh dipakai, validasi input, jumlah draft, teks
// status, dan penanganan hasil. SEMUA batas (kuota harian, jumlah draft, panjang requirement) dibaca dari `usage` yang dikirim
// backend -- angkanya TIDAK pernah ditulis ulang di FE, jadi mengubah batas tier di backend tidak membutuhkan perubahan di sini.
import type {
  CommitItemError,
  CommitTestCasesResult,
  GenerateTestCasesRequest,
  TestCaseAiGeneration,
  TestCaseAiGenerationStatus,
  TestCaseAiUsage,
} from '../types/testCaseAi.types';

export const PRIVACY_NOTICE =
  'Teks requirement dikirim ke layanan AI pihak ketiga untuk diproses. Jangan menyertakan kata sandi, token, atau data pribadi/rahasia.';

export const SANDBOX_WARNING =
  'Mode uji: jangan masukkan data asli. Pada mode ini penyedia layanan AI dapat memakai teks Anda untuk memperbaiki produknya.';

export const DRAFT_NOT_SAVED_NOTE =
  'Hasil AI hanyalah draft. Periksa dan edit sebelum disimpan; belum ada test case yang tersimpan.';

export const IN_PROGRESS_HINT =
  'Proses sebelumnya mungkin masih berjalan. Tunggu beberapa saat, lalu buka kembali jendela ini; hasilnya akan muncul sebagai draft terakhir.';

export const POLL_TIMEOUT_MESSAGE =
  'Proses generate memakan waktu terlalu lama. Hasilnya masih bisa muncul nanti: buka kembali Generate with AI untuk melihat draft terakhir.';

/**
 * Alasan tombol "Generate with AI" nonaktif, atau null kalau boleh dipakai. Status yang belum diketahui (usage belum termuat atau
 * gagal dimuat) juga memblokir: lebih baik tombol nonaktif dengan alasan jelas daripada membuka modal yang pasti gagal.
 */
export function aiBlockReason(usage: TestCaseAiUsage | null, loadFailed: boolean): string | null {
  if (usage === null) {
    return loadFailed ? 'Fitur generate tidak dapat dimuat saat ini.' : 'Memuat status fitur...';
  }
  if (!usage.aiEnabled) {
    return 'Fitur generate belum diaktifkan.';
  }
  if (usage.dailyLimit > 0 && usage.usedToday >= usage.dailyLimit) {
    return `Batas generate harian tercapai (${usage.dailyLimit} per hari). Coba lagi besok.`;
  }
  return null;
}

/** Sisa generate hari ini, atau null kalau tanpa batas harian. */
export function remainingToday(usage: TestCaseAiUsage): number | null {
  if (usage.dailyLimit <= 0) return null;
  return Math.max(0, usage.dailyLimit - usage.usedToday);
}

export function quotaLabel(usage: TestCaseAiUsage): string {
  if (usage.dailyLimit <= 0) return 'Tanpa batas generate harian';
  return `Hari ini: ${usage.usedToday} dari ${usage.dailyLimit} generate terpakai`;
}

// ---------- Input ----------

/** Backend menghitung panjang setelah spasi pinggir dibuang; FE menghitung dengan cara yang sama. */
export function requirementLength(text: string): number {
  return text.trim().length;
}

export function requirementError(text: string, max: number): string | null {
  const length = requirementLength(text);
  if (length === 0) return 'Requirement wajib diisi.';
  if (length > max) return `Requirement maksimal ${max} karakter.`;
  return null;
}

export function requirementCounterText(text: string, max: number): string {
  return `${requirementLength(text)} / ${max}`;
}

/** Jumlah draft yang diminta -> bilangan bulat 1..max (nilai bukan angka -> max, yaitu bawaan backend). */
export function clampCount(value: unknown, max: number): number {
  const safeMax = Math.max(1, Math.floor(max));
  // Nilai kosong = tidak memilih: bawaan backend (batas tier). Tanpa ini Number(null) bernilai 0 dan diam-diam menjadi 1.
  if (value === null || value === undefined || value === '') return safeMax;
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed)) return safeMax;
  return Math.min(safeMax, Math.max(1, Math.floor(parsed)));
}

export function canSubmit(usage: TestCaseAiUsage | null, requirement: string, submitting: boolean): { ok: boolean; reason: string | null } {
  if (submitting) return { ok: false, reason: null };
  if (usage === null) return { ok: false, reason: 'Memuat status fitur...' };
  const blocked = aiBlockReason(usage, false);
  if (blocked !== null) return { ok: false, reason: blocked };
  const error = requirementError(requirement, usage.maxRequirementChars);
  return error === null ? { ok: true, reason: null } : { ok: false, reason: error };
}

export function toGenerateRequest(
  folderId: string,
  requirement: string,
  count: unknown,
  includeNegative: boolean,
  usage: TestCaseAiUsage,
): GenerateTestCasesRequest {
  return {
    folderId,
    requirement: requirement.trim(),
    count: clampCount(count, usage.maxDraftsPerGeneration),
    includeNegative,
  };
}

// ---------- Proses ----------

/** Teks status yang ramah selama menunggu (status nyata + waktu berjalan, tanpa persentase palsu). */
export function progressText(status: TestCaseAiGenerationStatus, elapsedMs: number): string {
  if (status === 'QUEUED') return 'Menunggu giliran diproses...';
  if (elapsedMs > 45_000) return 'Masih menyusun draft test case. Ini bisa memakan waktu lebih lama untuk banyak draft.';
  return 'Menyusun draft test case dari requirement Anda...';
}

// ---------- Hasil & review ----------

/** "N dari M draft" kalau hasilnya kurang dari yang diminta (mis. keluaran AI terpotong); null kalau lengkap. */
export function draftCountNotice(generation: TestCaseAiGeneration): string | null {
  if (generation.draftCount < generation.requestedCount) {
    const base = `${generation.draftCount} dari ${generation.requestedCount} draft yang diminta`;
    return generation.truncated
      ? `${base}. Keluaran AI terpotong, jadi hanya draft yang lengkap yang ditampilkan. Anda bisa generate ulang untuk hasil yang lebih lengkap.`
      : `${base}.`;
  }
  return generation.truncated ? 'Keluaran AI terpotong; hanya draft yang lengkap yang ditampilkan.' : null;
}

export function pendingBannerText(generation: TestCaseAiGeneration): string {
  const where = generation.folderName ? ` untuk folder "${generation.folderName}"` : '';
  return `Ada ${generation.draftCount} draft yang belum disimpan${where}.`;
}

/**
 * Folder tujuan commit. Biasanya folder saat draft dibuat; kalau folder itu sudah dihapus (folderName null), jatuh ke folder
 * yang sedang dibuka supaya draft tidak hilang.
 */
export function commitTarget(
  generation: TestCaseAiGeneration,
  current: { folderId: string; folderName: string },
): { folderId: string; folderName: string } {
  return generation.folderName !== null
    ? { folderId: generation.folderId, folderName: generation.folderName }
    : { folderId: current.folderId, folderName: current.folderName };
}

export function savedMessage(result: CommitTestCasesResult): string {
  return `${result.savedCount} test case ditambahkan ke folder "${result.folderName}".`;
}

/** Kode error commit yang artinya draft ini sudah tidak bisa disimpan lagi (harus generate ulang / sudah tersimpan). */
const DRAFT_GONE_CODES = new Set(['GENERATION_ALREADY_COMMITTED', 'DRAFT_EXPIRED', 'GENERATION_NOT_FOUND', 'GENERATION_NOT_READY']);

export function isDraftGoneCode(errorCode: string | null): boolean {
  return errorCode !== null && DRAFT_GONE_CODES.has(errorCode);
}

/** Generate ulang memakan jatah baru: tidak boleh kalau jatah harian sudah habis. */
export function canRegenerate(usage: TestCaseAiUsage | null): boolean {
  if (usage === null) return false;
  const remaining = remainingToday(usage);
  return remaining === null || remaining > 0;
}

export function regenerateWarning(usage: TestCaseAiUsage | null): string {
  const remaining = usage === null ? null : remainingToday(usage);
  const quota = remaining === null ? '1 jatah generate' : `1 jatah generate (sisa ${remaining})`;
  return `Generate ulang memakai ${quota} dan membuang draft beserta editan Anda saat ini.`;
}

/** Membaca daftar `errors` dari body 400 INVALID_DRAFTS; entri yang bentuknya salah dibuang. */
export function extractCommitErrors(data: unknown): CommitItemError[] {
  if (!data || typeof data !== 'object') return [];
  const raw = (data as { errors?: unknown }).errors;
  if (!Array.isArray(raw)) return [];
  const result: CommitItemError[] = [];
  for (const entry of raw) {
    if (entry && typeof entry === 'object') {
      const item = entry as { index?: unknown; field?: unknown; message?: unknown };
      if (typeof item.index === 'number' && typeof item.field === 'string' && typeof item.message === 'string') {
        result.push({ index: item.index, field: item.field, message: item.message });
      }
    }
  }
  return result;
}

// frontend/src/shared/utils/aiPolling.ts
// Polling status job AI (generate kode automation, generate test case). Proses AI di backend bisa puluhan detik, jadi
// POST hanya membuat job (202) dan klien menanyakan statusnya berkala sampai SUCCEEDED/FAILED. Logika dipisah dari Vue
// (sleep/now/cancel diinjeksi) supaya bisa diuji tanpa timer sungguhan. Generik: tipe hasil ditentukan fitur pemanggil,
// asal punya field `status`.

/** Status job AI di backend (sama utk semua fitur): QUEUED -> RUNNING -> SUCCEEDED | FAILED. */
export type AiJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

/** Berhenti menunggu setelah ini (job tetap berjalan di server; hasilnya masih bisa dibuka lagi nanti). */
export const POLL_GIVE_UP_MS = 10 * 60 * 1000;
/** Gagal berturut-turut (jaringan putus dsb) sebelum polling menyerah. */
export const MAX_CONSECUTIVE_ERRORS = 3;

export function isActiveStatus(status: AiJobStatus): boolean {
  return status === 'QUEUED' || status === 'RUNNING';
}

export function isFinishedStatus(status: AiJobStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED';
}

/** Jeda antar polling: cepat di awal, lalu melonggar (hemat request utk job yang lama). */
export function nextPollDelayMs(attempt: number): number {
  if (attempt < 5) return 2000;
  if (attempt < 15) return 3000;
  return 5000;
}

export type PollOutcome<T extends { status: AiJobStatus }> =
  | { kind: 'finished'; generation: T }
  | { kind: 'timeout' }
  | { kind: 'error'; error: unknown }
  | { kind: 'cancelled' };

export interface PollOptions<T extends { status: AiJobStatus }> {
  fetchOne: () => Promise<T>;
  sleep: (ms: number) => Promise<void>;
  now: () => number;
  isCancelled: () => boolean;
  /** Dipanggil setiap status berhasil diambil (utk memperbarui tampilan progres). */
  onUpdate?: (generation: T) => void;
  /** true = error ini tidak masuk akal dicoba ulang (mis. 403/404): berhenti seketika. */
  isFatal?: (error: unknown) => boolean;
  giveUpMs?: number;
  maxConsecutiveErrors?: number;
}

export async function pollUntilFinished<T extends { status: AiJobStatus }>(options: PollOptions<T>): Promise<PollOutcome<T>> {
  const giveUpMs = options.giveUpMs ?? POLL_GIVE_UP_MS;
  const maxErrors = options.maxConsecutiveErrors ?? MAX_CONSECUTIVE_ERRORS;
  const startedAt = options.now();
  let attempt = 0;
  let consecutiveErrors = 0;

  for (;;) {
    if (options.isCancelled()) return { kind: 'cancelled' };
    if (options.now() - startedAt > giveUpMs) return { kind: 'timeout' };

    try {
      const generation = await options.fetchOne();
      consecutiveErrors = 0;
      if (options.isCancelled()) return { kind: 'cancelled' };
      options.onUpdate?.(generation);
      if (isFinishedStatus(generation.status)) {
        return { kind: 'finished', generation };
      }
    } catch (error) {
      if (options.isCancelled()) return { kind: 'cancelled' };
      consecutiveErrors++;
      if ((options.isFatal?.(error) ?? false) || consecutiveErrors >= maxErrors) {
        return { kind: 'error', error };
      }
    }

    await options.sleep(nextPollDelayMs(attempt++));
  }
}

/** Status HTTP yang tidak masuk akal dicoba ulang saat polling (sesi habis, tidak berhak, job tidak ada): berhenti seketika. */
export function isFatalPollStatus(status: number | undefined): boolean {
  return status === 401 || status === 403 || status === 404;
}

/** 7000 -> "0:07", 125000 -> "2:05". */
export function formatElapsed(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

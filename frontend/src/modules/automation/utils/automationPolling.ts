// frontend/src/modules/automation/utils/automationPolling.ts
// Mesin polling kini ada di shared (dipakai bersama fitur AI lain). Berkas ini mempertahankan nama lama dan hanya
// menyimpan teks progres yang KHAS automation.
import type { AutomationGeneration, AutomationGenerationStatus } from '../types/automation.types';
import type { PollOptions as SharedPollOptions, PollOutcome as SharedPollOutcome } from '../../../shared/utils/aiPolling';

export {
  POLL_GIVE_UP_MS,
  MAX_CONSECUTIVE_ERRORS,
  isActiveStatus,
  isFinishedStatus,
  nextPollDelayMs,
  pollUntilFinished,
  formatElapsed,
} from '../../../shared/utils/aiPolling';

export type PollOutcome = SharedPollOutcome<AutomationGeneration>;
export type PollOptions = SharedPollOptions<AutomationGeneration>;

/** Teks status yang ramah selama menunggu. */
export function progressText(status: AutomationGenerationStatus, elapsedMs: number): string {
  if (status === 'QUEUED') return 'Menunggu giliran diproses...';
  if (elapsedMs > 60_000) return 'Masih menyusun kode. Proses ini bisa memakan waktu beberapa menit untuk banyak test case.';
  return 'Menyusun kode automation dari test case Anda...';
}

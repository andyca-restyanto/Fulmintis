// frontend/src/modules/testrun/utils/runStatusDisplay.ts
import type { TestRunStatus } from '../types/testRun.types';

// Tampilan status test run (badge + label). Disamakan dgn yang dipakai
// TestRunsView dan ReportsView; dashboard project memakai ini lewat
// index.ts modul testrun.
export const RUN_STATUS_BADGE_CLASS: Record<TestRunStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  RUNNING: 'bg-blue-50 text-blue-600',
  FINISHED: 'bg-emerald-50 text-emerald-700',
};

export const RUN_STATUS_LABEL: Record<TestRunStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  FINISHED: 'Finished',
};

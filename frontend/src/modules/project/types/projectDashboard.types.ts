// frontend/src/modules/project/types/projectDashboard.types.ts
import type { TestRunStatus } from '@/modules/testrun';

// ---- Match 100% dgn dto di backend: modules/dashboard/dto/* ----
// Respons GET /api/projects/{projectId}/dashboard (ProjectDashboardResponseDTO).
// Semua hitungan test case HANYA menghitung yang ACTIVE (arsip terpisah di
// archivedTestCases).

/** ScenarioBreakdownDTO */
export interface ScenarioBreakdown {
  positive: number;
  negative: number;
}

/** PriorityBreakdownDTO */
export interface PriorityBreakdown {
  highest: number;
  high: number;
  medium: number;
  low: number;
}

/**
 * HealthBreakdownDTO -- dihitung SAMA dengan Report (tab Overview): setiap HASIL
 * EKSEKUSI dari semua test run di project, per status. Keempat angka identik
 * dengan totalPassed / totalFailed / totalBlocked / totalPending di
 * GET /report/overview; jumlahnya = totalExecutions di Report (BUKAN
 * totalTestCases: test case yang sama di 2 run dihitung 2x, dan test case yang
 * belum masuk test run tidak dihitung).
 * `pending` = NEW + PENDING; di dashboard DILABELI "Not Run" (nama field di API
 * tetap `pending` agar identik dengan Report).
 */
export interface HealthBreakdown {
  passed: number;
  failed: number;
  blocked: number;
  pending: number;
}

/** DashboardRunDTO */
export interface DashboardRun {
  id: string;
  title: string;
  status: TestRunStatus;
  testCaseCount: number;
  passedCount: number;
  failedCount: number;
  blockedCount: number;
  /** (passed + failed + blocked) / testCaseCount * 100, dibulatkan; 0 kalau run kosong. */
  progressPercentage: number;
  createdAt: string;
}

/** TimelinePointDTO -- date = "yyyy-MM-dd" (hari di zona waktu server). */
export interface TimelinePoint {
  date: string;
  passed: number;
  failed: number;
  blocked: number;
}

/** ProjectDashboardResponseDTO */
export interface ProjectDashboardResponse {
  totalTestCases: number;
  archivedTestCases: number;
  scenario: ScenarioBreakdown;
  priority: PriorityBreakdown;
  health: HealthBreakdown;
  /** null kalau project belum punya run. */
  latestRun: DashboardRun | null;
  /** Belum FINISHED DAN belum 100% dieksekusi; maks 5, terbaru dulu. */
  activeRuns: DashboardRun[];
  /** SELALU 30 elemen, terlama -> hari ini; hari tanpa eksekusi bernilai 0. */
  executionTimeline: TimelinePoint[];
}

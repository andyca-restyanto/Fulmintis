<!-- frontend/src/modules/report/views/ReportsView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import axios from 'axios';
import { useProjectDetail } from '@/modules/project/composables/useProjectDetail';
import { reportService } from '../services/report.service';
import type { ReportOverviewResponse } from '../types/report.types';
import type { TestRunDetailResponse, TestRunStatus, TestResultStatus } from '@/modules/testrun';
import type { TestCasePriority, TestCaseType, TestCaseScenarioType } from '@/modules/testcase';
import { priorityBadgeClass } from '@/modules/testcase/utils/testCaseDisplay';
import DownloadIcon from '@/shared/components/icons/DownloadIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import ChevronRightIcon from '@/shared/components/icons/ChevronRightIcon.vue';

// Halaman menu "Reports" (requirement #1-3): 2 tab -- Overview (data dari
// GET /report/overview) & Run Details (GET /report/run-details, requirement
// #1-2: expand 1 baris test run -> tampil seluruh test case + hasil
// eksekusinya, lihat mockup). Tombol Export Excel memanggil GET
// /report/export (file .xlsx asli dari Apache POI, lihat ReportController
// backend) -- BUKAN lagi CSV client-side. Flow "Add New Test Run"
// (requirement flow #2-6) dilakukan lewat menu Test Runs yang sudah ada.
const project = useProjectDetail();

type Tab = 'overview' | 'run-details';
const activeTab = ref<Tab>('overview');

// ---- Tab Overview ----
const overview = ref<ReportOverviewResponse | null>(null);
const isOverviewLoading = ref(true);
const overviewError = ref('');

async function loadOverview() {
  isOverviewLoading.value = true;
  overviewError.value = '';
  try {
    overview.value = await reportService.getOverview(project.value?.id ?? '');
  } catch {
    overviewError.value = 'Gagal memuat data report.';
  } finally {
    isOverviewLoading.value = false;
  }
}

interface StatCard {
  label: string;
  value: string;
  valueClass: string;
}

const statCards = computed<StatCard[]>(() => {
  const o = overview.value;
  if (!o) return [];
  return [
    { label: 'Total Runs', value: String(o.totalTestRuns), valueClass: 'text-gray-900' },
    { label: 'Total Executions', value: String(o.totalExecutions), valueClass: 'text-gray-900' },
    { label: 'Passed', value: String(o.totalPassed), valueClass: 'text-emerald-600' },
    { label: 'Failed', value: String(o.totalFailed), valueClass: 'text-red-600' },
    { label: 'Blocked', value: String(o.totalBlocked), valueClass: 'text-amber-500' },
    { label: 'Pass Rate', value: `${o.passRatePercentage}%`, valueClass: 'text-gray-900' },
  ];
});

interface DistributionSegment {
  label: string;
  count: number;
  dotClass: string;
  barClass: string;
}

const distributionSegments = computed<DistributionSegment[]>(() => {
  const o = overview.value;
  if (!o) return [];
  return [
    { label: 'Passed', count: o.totalPassed, dotClass: 'bg-emerald-500', barClass: 'bg-emerald-500' },
    { label: 'Failed', count: o.totalFailed, dotClass: 'bg-red-500', barClass: 'bg-red-500' },
    { label: 'Blocked', count: o.totalBlocked, dotClass: 'bg-amber-400', barClass: 'bg-amber-400' },
    { label: 'Pending', count: o.totalPending, dotClass: 'bg-gray-300', barClass: 'bg-gray-200' },
  ];
});

// Lebar tiap segmen proporsional thd totalExecutions; kalau belum ada
// eksekusi sama sekali, bar digambar kosong (bukan error) sbg placeholder.
function segmentWidthPercent(count: number): number {
  const total = overview.value?.totalExecutions ?? 0;
  return total === 0 ? 0 : (count / total) * 100;
}

// ---- Tab Run Details ----
const testRuns = ref<TestRunDetailResponse[]>([]);
const isRunDetailsLoading = ref(true);
const runDetailsError = ref('');
const expandedRunIds = ref<Set<string>>(new Set());
let runDetailsLoaded = false;

async function loadRunDetails() {
  isRunDetailsLoading.value = true;
  runDetailsError.value = '';
  try {
    testRuns.value = await reportService.getRunDetails(project.value?.id ?? '');
    runDetailsLoaded = true;
  } catch {
    runDetailsError.value = 'Gagal memuat data run details.';
  } finally {
    isRunDetailsLoading.value = false;
  }
}

// Lazy-load: tab Run Details baru fetch saat pertama kali dibuka, bukan
// bersamaan dgn Overview di awal -- halaman ini tetap ringan kalau user
// cuma butuh tab Overview.
watch(activeTab, (tab) => {
  if (tab === 'run-details' && !runDetailsLoaded) {
    loadRunDetails();
  }
});

onMounted(loadOverview);

function toggleRunExpanded(runId: string) {
  const next = new Set(expandedRunIds.value);
  if (next.has(runId)) {
    next.delete(runId);
  } else {
    next.add(runId);
  }
  expandedRunIds.value = next;
}

const RUN_STATUS_BADGE_CLASS: Record<TestRunStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  RUNNING: 'bg-blue-50 text-blue-600',
  FINISHED: 'bg-emerald-50 text-emerald-700',
};
const RUN_STATUS_LABEL: Record<TestRunStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  FINISHED: 'Finished',
};
const RESULT_STATUS_LABEL: Record<TestResultStatus, string> = {
  NEW: 'New',
  PENDING: 'Pending',
  PASSED: 'Passed',
  FAILED: 'Failed',
  BLOCKED: 'Blocked',
};
const RESULT_STATUS_TEXT_CLASS: Record<TestResultStatus, string> = {
  NEW: 'text-gray-400',
  PENDING: 'text-gray-500',
  PASSED: 'text-emerald-600',
  FAILED: 'text-red-600',
  BLOCKED: 'text-amber-600',
};
const PRIORITY_LABEL: Record<TestCasePriority, string> = { HIGHEST: 'Highest', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };
const TYPE_LABEL: Record<TestCaseType, string> = { MANUAL: 'Manual', AUTOMATION: 'Automation' };
const SCENARIO_LABEL: Record<TestCaseScenarioType, string> = { POSITIVE: 'Positive', NEGATIVE: 'Negative' };

function runPassedCount(run: TestRunDetailResponse) {
  return run.testResults.filter((r) => r.status === 'PASSED').length;
}
function runFailedCount(run: TestRunDetailResponse) {
  return run.testResults.filter((r) => r.status === 'FAILED').length;
}
// Progress = Execution Rate, rumus SAMA dgn backend (ReportServiceImpl):
// (passed + failed) / total test case x 100. BLOCKED / NEW / PENDING belum
// dihitung sebagai "sudah dieksekusi".
function runProgressPercent(run: TestRunDetailResponse) {
  const total = run.testResults.length;
  if (total === 0) return 0;
  return Math.round(((runPassedCount(run) + runFailedCount(run)) / total) * 100);
}
function formatDate(isoDateTime: string): string {
  return new Date(isoDateTime).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

// ---- Export Excel ----
const isExporting = ref(false);
const exportError = ref('');

async function handleExport() {
  exportError.value = '';
  isExporting.value = true;
  try {
    await reportService.downloadExport(project.value?.id ?? '');
  } catch (err) {
    exportError.value = axios.isAxiosError(err)
      ? 'Gagal download report, coba lagi.'
      : 'Tidak dapat terhubung ke server.';
  } finally {
    isExporting.value = false;
  }
}
</script>

<template>
  <div class="px-8 py-8">
    <div class="flex items-start justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Reports</h1>
        <p class="mt-1 text-sm text-gray-400">Execution reports across all test runs</p>
      </div>
      <div class="shrink-0 text-right">
        <button
          type="button"
          :disabled="isExporting"
          class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
          @click="handleExport"
        >
          <DownloadIcon :size="15" />
          {{ isExporting ? 'Exporting...' : 'Export Excel' }}
        </button>
        <p v-if="exportError" class="mt-1.5 text-xs text-red-600">{{ exportError }}</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mt-6 inline-flex rounded-xl bg-gray-100 p-1">
      <button
        type="button"
        class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
        :class="activeTab === 'overview' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'"
        @click="activeTab = 'overview'"
      >
        Overview
      </button>
      <button
        type="button"
        class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
        :class="activeTab === 'run-details' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'"
        @click="activeTab = 'run-details'"
      >
        Run Details
      </button>
    </div>

    <!-- ===== Tab Overview ===== -->
    <template v-if="activeTab === 'overview'">
      <p v-if="isOverviewLoading" class="mt-8 text-sm text-gray-400">Memuat data report...</p>
      <p v-else-if="overviewError" class="mt-8 text-sm text-red-500">{{ overviewError }}</p>
      <template v-else>
        <div class="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <div v-for="card in statCards" :key="card.label" class="rounded-2xl border border-gray-100 px-5 py-6 text-center">
            <p class="text-3xl font-bold" :class="card.valueClass">{{ card.value }}</p>
            <p class="mt-1 text-sm text-gray-400">{{ card.label }}</p>
          </div>
        </div>

        <div class="mt-6 rounded-2xl border border-gray-100 px-6 py-6">
          <h2 class="text-base font-semibold text-gray-900">Overall Execution Distribution</h2>
          <div class="mt-4 flex h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
            <div
              v-for="segment in distributionSegments"
              :key="segment.label"
              class="h-full"
              :class="segment.barClass"
              :style="{ width: `${segmentWidthPercent(segment.count)}%` }"
            />
          </div>
          <div class="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-600">
            <span v-for="segment in distributionSegments" :key="segment.label" class="flex items-center gap-2">
              <span class="h-2.5 w-2.5 rounded-full" :class="segment.dotClass" />
              {{ segment.label }}: {{ segment.count }}
            </span>
          </div>
        </div>
      </template>
    </template>

    <!-- ===== Tab Run Details ===== -->
    <template v-else>
      <p v-if="isRunDetailsLoading" class="mt-8 text-sm text-gray-400">Memuat data run details...</p>
      <p v-else-if="runDetailsError" class="mt-8 text-sm text-red-500">{{ runDetailsError }}</p>
      <p v-else-if="testRuns.length === 0" class="mt-8 text-sm text-gray-400">Belum ada test run di project ini.</p>

      <div v-else class="mt-6 overflow-hidden rounded-2xl border border-gray-100">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-gray-100 text-xs font-semibold uppercase tracking-wide text-gray-400">
              <th class="w-10 py-3 pl-4"></th>
              <th class="py-3 pr-4 font-semibold">Run Title</th>
              <th class="px-4 py-3 font-semibold">Status</th>
              <th class="px-4 py-3 font-semibold">Cases</th>
              <th class="px-4 py-3 font-semibold">Passed</th>
              <th class="px-4 py-3 font-semibold">Failed</th>
              <th class="px-4 py-3 font-semibold">Progress</th>
              <th class="px-4 py-3 pr-4 font-semibold">Created</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="run in testRuns" :key="run.id">
              <tr
                class="cursor-pointer border-b border-gray-50 last:border-0 hover:bg-gray-50"
                @click="toggleRunExpanded(run.id)"
              >
                <td class="py-3 pl-4 text-gray-400">
                  <ChevronDownIcon v-if="expandedRunIds.has(run.id)" :size="15" />
                  <ChevronRightIcon v-else :size="15" />
                </td>
                <td class="py-3 pr-4 font-semibold text-gray-900">{{ run.title }}</td>
                <td class="px-4 py-3">
                  <span class="rounded-full px-2.5 py-1 text-xs font-medium" :class="RUN_STATUS_BADGE_CLASS[run.status]">
                    {{ RUN_STATUS_LABEL[run.status] }}
                  </span>
                </td>
                <td class="px-4 py-3 text-gray-700">{{ run.testResults.length }}</td>
                <td class="px-4 py-3 font-medium text-emerald-600">{{ runPassedCount(run) }}</td>
                <td class="px-4 py-3 font-medium text-red-600">{{ runFailedCount(run) }}</td>
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <div class="h-1.5 w-24 overflow-hidden rounded-full bg-gray-100">
                      <div class="h-full rounded-full bg-gray-900" :style="{ width: `${runProgressPercent(run)}%` }" />
                    </div>
                    <span class="text-xs text-gray-400">{{ runProgressPercent(run) }}%</span>
                  </div>
                </td>
                <td class="px-4 py-3 pr-4 text-gray-500">{{ formatDate(run.createdAt) }}</td>
              </tr>

              <tr v-if="expandedRunIds.has(run.id)" class="border-b border-gray-50 bg-gray-50/60 last:border-0">
                <td colspan="8" class="px-4 py-4 pl-12">
                  <p v-if="run.testResults.length === 0" class="text-sm text-gray-400">
                    Belum ada test case yang di-mapping ke run ini.
                  </p>
                  <table v-else class="w-full text-left text-sm">
                    <thead>
                      <tr class="text-xs font-semibold uppercase tracking-wide text-gray-400">
                        <th class="py-2 pr-4 font-semibold">Test Case</th>
                        <th class="px-4 py-2 font-semibold">Priority</th>
                        <th class="px-4 py-2 font-semibold">Type</th>
                        <th class="px-4 py-2 font-semibold">Scenario Type</th>
                        <th class="px-4 py-2 font-semibold">Status</th>
                        <th class="px-4 py-2 font-semibold">Comment</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="result in run.testResults" :key="result.id" class="border-t border-gray-100">
                        <td class="py-2.5 pr-4 text-gray-900">{{ result.testCaseTitle }}</td>
                        <td class="px-4 py-2.5">
                          <span
                            class="inline-block rounded-full px-2.5 py-0.5 text-xs font-medium"
                            :class="priorityBadgeClass(result.testCasePriority)"
                          >
                            {{ PRIORITY_LABEL[result.testCasePriority] }}
                          </span>
                        </td>
                        <td class="px-4 py-2.5 text-gray-600">{{ TYPE_LABEL[result.testCaseType] }}</td>
                        <td class="px-4 py-2.5 text-gray-600">
                          {{ result.testCaseScenarioType ? SCENARIO_LABEL[result.testCaseScenarioType] : '-' }}
                        </td>
                        <td class="px-4 py-2.5 font-medium" :class="RESULT_STATUS_TEXT_CLASS[result.status]">
                          {{ RESULT_STATUS_LABEL[result.status] }}
                        </td>
                        <td class="px-4 py-2.5 text-gray-500">{{ result.comment || '-' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

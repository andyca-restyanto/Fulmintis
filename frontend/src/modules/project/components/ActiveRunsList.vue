<!-- frontend/src/modules/project/components/ActiveRunsList.vue -->
<script setup lang="ts">
import { RUN_STATUS_BADGE_CLASS, RUN_STATUS_LABEL } from '@/modules/testrun';
import type { DashboardRun } from '../types/projectDashboard.types';

// Run yang masih berjalan: belum FINISHED DAN belum 100% dieksekusi (maks 5,
// terbaru dulu -- sudah disaring backend). Run yang semua test-nya sudah
// dieksekusi tapi berisi FAILED sengaja TIDAK tampil di sini.
defineProps<{
  runs: DashboardRun[];
  projectId: string;
}>();

function segments(run: DashboardRun) {
  const rest = Math.max(run.testCaseCount - run.passedCount - run.failedCount - run.blockedCount, 0);
  return [
    { key: 'passed', count: run.passedCount, class: 'bg-emerald-500' },
    { key: 'failed', count: run.failedCount, class: 'bg-red-500' },
    { key: 'blocked', count: run.blockedCount, class: 'bg-amber-400' },
    { key: 'rest', count: rest, class: 'bg-gray-200' },
  ].filter((segment) => segment.count > 0);
}

function executedCount(run: DashboardRun): number {
  return run.passedCount + run.failedCount + run.blockedCount;
}

function formatDate(value: string): string {
  try {
    return new Date(value).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  } catch {
    return '';
  }
}
</script>

<template>
  <div>
    <div v-if="runs.length === 0" class="flex h-24 items-center justify-center text-sm text-gray-400">
      No active runs
    </div>

    <ul v-else class="divide-y divide-gray-100">
      <li v-for="run in runs" :key="run.id">
        <RouterLink
          :to="{ name: 'project-test-run-execute', params: { projectId, testRunId: run.id } }"
          class="block rounded-xl px-2 py-4 transition-colors hover:bg-gray-50"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex min-w-0 items-center gap-2.5">
              <span class="truncate text-sm font-semibold text-gray-900">{{ run.title }}</span>
              <span
                class="shrink-0 rounded-full px-2.5 py-1 text-xs font-medium"
                :class="RUN_STATUS_BADGE_CLASS[run.status]"
              >
                {{ RUN_STATUS_LABEL[run.status] }}
              </span>
            </div>
            <span class="shrink-0 text-xs text-gray-400">{{ formatDate(run.createdAt) }}</span>
          </div>

          <div
            class="mt-3 flex h-2 w-full overflow-hidden rounded-full bg-gray-100"
            role="progressbar"
            :aria-valuenow="run.progressPercentage"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-label="`Progress ${run.title}`"
          >
            <div
              v-for="segment in segments(run)"
              :key="segment.key"
              :class="segment.class"
              :style="{ width: `${(segment.count / run.testCaseCount) * 100}%` }"
            />
          </div>

          <div class="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500">
            <span>
              <span class="font-semibold text-gray-700">{{ executedCount(run) }}/{{ run.testCaseCount }}</span>
              executed &middot; {{ run.progressPercentage }}%
            </span>
            <span class="flex items-center gap-3">
              <span class="flex items-center gap-1"><span class="h-1.5 w-1.5 rounded-full bg-emerald-500" />{{ run.passedCount }}</span>
              <span class="flex items-center gap-1"><span class="h-1.5 w-1.5 rounded-full bg-red-500" />{{ run.failedCount }}</span>
              <span class="flex items-center gap-1"><span class="h-1.5 w-1.5 rounded-full bg-amber-400" />{{ run.blockedCount }}</span>
            </span>
          </div>
        </RouterLink>
      </li>
    </ul>
  </div>
</template>

<!-- frontend/src/modules/project/views/ProjectDashboardView.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { RUN_STATUS_BADGE_CLASS, RUN_STATUS_LABEL } from '@/modules/testrun';
import { useProjectDetail } from '../composables/useProjectDetail';
import { useProjectDashboardData } from '../composables/useProjectDashboardData';
import ExecutionTimelineChart from '../components/ExecutionTimelineChart.vue';
import ProjectHealthChart from '../components/ProjectHealthChart.vue';
import PriorityBarsChart from '../components/PriorityBarsChart.vue';
import ActiveRunsList from '../components/ActiveRunsList.vue';
import IntegrationsCard from '../components/IntegrationsCard.vue';
import CalendarIcon from '@/shared/components/icons/CalendarIcon.vue';
import PencilIcon from '@/shared/components/icons/PencilIcon.vue';
import ScaleIcon from '@/shared/components/icons/ScaleIcon.vue';
import PlayIcon from '@/shared/components/icons/PlayIcon.vue';
import UsersIcon from '@/shared/components/icons/UsersIcon.vue';

// Detail project (nama, tanggal dibuat, jumlah member, dst) sudah di-fetch
// SEKALI oleh ProjectLayoutView lewat GET /api/projects/{id}, di-share ke
// sini lewat provide/inject (lihat composables/useProjectDetail.ts).
const project = useProjectDetail();

// Angka dashboard (test case, health, run, timeline) diambil lewat SATU
// panggilan GET /api/projects/{id}/dashboard, dimuat ulang setiap halaman ini
// dibuka sehingga hasil eksekusi terbaru langsung terlihat.
const route = useRoute();
const projectId = computed(() => String(route.params.projectId));
const { data, isLoading, errorMessage, reload } = useProjectDashboardData(projectId);

const formattedCreatedAt = computed(() => {
  if (!project.value) return '';
  try {
    return new Date(project.value.createdAt).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '';
  }
});
</script>

<template>
  <div v-if="project" class="px-8 py-8">
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900">{{ project.projectName }}</h1>
      <p class="mt-1 flex items-center gap-1.5 text-sm text-gray-400">
        <CalendarIcon :size="14" />
        Created {{ formattedCreatedAt }}
      </p>
    </div>

    <!-- Stat cards -->
    <div class="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <!-- Total Test Cases (hanya yang ACTIVE; arsip dihitung terpisah) -->
      <div class="rounded-2xl border border-gray-200 p-5">
        <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-500">
          <PencilIcon :size="18" />
        </div>
        <p class="text-sm text-gray-500">Total Test Cases</p>
        <template v-if="data">
          <p class="mt-1 text-2xl font-bold text-gray-900">{{ data.totalTestCases }}</p>
          <p v-if="data.archivedTestCases > 0" class="mt-0.5 text-xs text-gray-400">
            {{ data.archivedTestCases }} archived
          </p>
        </template>
        <div v-else-if="isLoading" class="mt-2 h-7 w-12 animate-pulse rounded-md bg-gray-100" />
        <p v-else class="mt-1 text-2xl font-bold text-gray-300">&mdash;</p>
      </div>

      <!-- Positive vs Negative -->
      <div class="rounded-2xl border border-gray-200 p-5">
        <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-500">
          <ScaleIcon :size="18" />
        </div>
        <p class="text-sm text-gray-500">Positive vs Negative</p>
        <p v-if="data" class="mt-1 text-2xl font-bold">
          <span class="text-emerald-600">{{ data.scenario.positive }}</span>
          <span class="mx-1 text-gray-300">/</span>
          <span class="text-red-500">{{ data.scenario.negative }}</span>
        </p>
        <div v-else-if="isLoading" class="mt-2 h-7 w-20 animate-pulse rounded-md bg-gray-100" />
        <p v-else class="mt-1 text-2xl font-bold text-gray-300">&mdash;</p>
      </div>

      <!-- Latest Run -->
      <div class="rounded-2xl border border-gray-200 p-5">
        <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-500">
          <PlayIcon :size="18" />
        </div>
        <p class="text-sm text-gray-500">Latest Run</p>
        <template v-if="data">
          <RouterLink
            v-if="data.latestRun"
            :to="{ name: 'project-test-run-execute', params: { projectId, testRunId: data.latestRun.id } }"
            class="mt-2 block rounded-lg transition-colors hover:text-gray-600"
          >
            <p class="truncate text-sm font-semibold text-gray-900" :title="data.latestRun.title">
              {{ data.latestRun.title }}
            </p>
            <p class="mt-1.5 flex items-center gap-2 text-xs text-gray-500">
              <span
                class="rounded-full px-2 py-0.5 font-medium"
                :class="RUN_STATUS_BADGE_CLASS[data.latestRun.status]"
              >
                {{ RUN_STATUS_LABEL[data.latestRun.status] }}
              </span>
              {{ data.latestRun.progressPercentage }}% executed
            </p>
          </RouterLink>
          <p v-else class="mt-2 text-sm font-semibold text-gray-400">No runs yet</p>
        </template>
        <div v-else-if="isLoading" class="mt-2 h-10 w-full animate-pulse rounded-md bg-gray-100" />
        <p v-else class="mt-2 text-sm font-semibold text-gray-300">&mdash;</p>
      </div>

      <!-- Team Members: dari detail project (sudah tersedia, tidak ikut menunggu dashboard) -->
      <div class="rounded-2xl border border-gray-200 p-5">
        <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-500">
          <UsersIcon :size="18" />
        </div>
        <p class="text-sm text-gray-500">Team Members</p>
        <p class="mt-1 text-2xl font-bold text-gray-900">{{ project.memberCount }}</p>
      </div>
    </div>

    <!-- Konten dashboard -->
    <template v-if="data">
      <!-- Project Health + Test Cases by Priority -->
      <div class="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="rounded-2xl border border-gray-200 p-6">
          <h2 class="mb-1 text-base font-bold text-gray-900">Current Project Health</h2>
          <ProjectHealthChart :health="data.health" />
        </div>

        <div class="rounded-2xl border border-gray-200 p-6">
          <h2 class="mb-6 text-base font-bold text-gray-900">Test Cases by Priority</h2>
          <PriorityBarsChart :priority="data.priority" />
        </div>
      </div>

      <!-- Execution Timeline -->
      <ExecutionTimelineChart class="mb-4" :points="data.executionTimeline" />

      <!-- Active Test Runs -->
      <div class="mb-4 rounded-2xl border border-gray-200 p-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-base font-bold text-gray-900">Active Test Runs</h2>
          <RouterLink
            :to="{ name: 'project-test-runs', params: { projectId } }"
            class="text-sm font-semibold text-gray-600 underline-offset-2 hover:text-gray-900 hover:underline"
          >
            View all
          </RouterLink>
        </div>
        <ActiveRunsList :runs="data.activeRuns" :project-id="projectId" />
      </div>
    </template>

    <!-- Gagal memuat -->
    <div
      v-else-if="errorMessage"
      class="mb-4 flex flex-col items-center justify-center gap-3 rounded-2xl border border-red-100 bg-red-50/40 px-6 py-14 text-center"
      role="alert"
    >
      <p class="text-sm font-medium text-red-700">{{ errorMessage }}</p>
      <button
        type="button"
        class="rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800"
        @click="reload"
      >
        Try again
      </button>
    </div>

    <!-- Memuat -->
    <template v-else>
      <div class="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-2" aria-busy="true">
        <div class="h-80 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
        <div class="h-80 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
      </div>
      <div class="mb-4 h-72 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
      <div class="mb-4 h-40 animate-pulse rounded-2xl border border-gray-200 bg-gray-50" />
    </template>

    <!-- Integrations -->
    <IntegrationsCard />
  </div>

  <div v-else class="flex min-h-[400px] items-center justify-center text-sm text-gray-400">
    Memuat data project...
  </div>
</template>

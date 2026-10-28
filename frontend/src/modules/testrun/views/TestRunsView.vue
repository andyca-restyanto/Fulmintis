<!-- frontend/src/modules/testrun/views/TestRunsView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { testRunService } from '../services/testRun.service';
import type { TestRunResponse, TestRunStatus, TestRunDetailResponse } from '../types/testRun.types';
import { useProjectDetail } from '@/modules/project/composables/useProjectDetail';
import CreateTestRunModal from '../components/CreateTestRunModal.vue';
import ManageTestRunCasesModal from '../components/ManageTestRunCasesModal.vue';
import DeleteTestRunModal from '../components/DeleteTestRunModal.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import SlidersIcon from '@/shared/components/icons/SlidersIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';
import PlayIcon from '@/shared/components/icons/PlayIcon.vue';

// Halaman menu "Test Runs" (requirement #1) -- persis mockup: empty state
// "No test runs yet." + "Create your first run", atau daftar card test run
// dgn status badge + progress bar passed/failed/blocked + aksi Manage
// Cases/Delete/Execute.
const route = useRoute();
const router = useRouter();
const projectId = computed(() => route.params.projectId as string);

// Requirement #6: create & delete test run HANYA boleh OWNER -- tombol
// "New Test Run"/"Create your first run"/"Delete" disembunyikan TOTAL dari
// COLLABORATOR (bukan cuma di-disable), backend tetap jadi penjaga akhir.
const project = useProjectDetail();
const isOwner = computed(() => project.value?.myProjectTeam === 'OWNER');

const testRuns = ref<TestRunResponse[]>([]);
const isLoading = ref(true);
const loadError = ref('');

async function loadTestRuns() {
  isLoading.value = true;
  loadError.value = '';
  try {
    testRuns.value = await testRunService.listTestRuns(projectId.value);
  } catch {
    loadError.value = 'Gagal memuat daftar test run.';
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadTestRuns);

// ---- Create (requirement #1, #6) ----
const isCreateModalOpen = ref(false);

function handleTestRunCreated() {
  isCreateModalOpen.value = false;
  loadTestRuns();
}

// ---- Manage Cases (requirement #2, #7) ----
const isManageCasesModalOpen = ref(false);
const manageCasesTargetId = ref<string | null>(null);
// ManageTestRunCasesModal butuh TestRunDetailResponse (buat tau test case
// mana yang HARUS di-exclude dari pilihan) -- di-fetch on-demand tiap kali
// tombol "Manage Cases" diklik, bukan disimpan permanen di listing ringkas.
const manageCasesTarget = ref<TestRunDetailResponse | null>(null);
const isLoadingManageCasesTarget = ref(false);

async function openManageCases(testRunId: string) {
  manageCasesTargetId.value = testRunId;
  isLoadingManageCasesTarget.value = true;
  isManageCasesModalOpen.value = true;
  try {
    manageCasesTarget.value = await testRunService.getTestRunDetail(projectId.value, testRunId);
  } catch {
    manageCasesTarget.value = null;
    isManageCasesModalOpen.value = false;
  } finally {
    isLoadingManageCasesTarget.value = false;
  }
}

function closeManageCases() {
  isManageCasesModalOpen.value = false;
  manageCasesTarget.value = null;
  manageCasesTargetId.value = null;
}

// Modal Manage Cases menutup dirinya sendiri setelah "Save changes"
// berhasil (checklist: uncheck = lepas, check = tambah) -- di sini cukup
// refresh listing di belakangnya (jumlah case, progress bar, status run).
function handleCasesUpdated(updated: TestRunDetailResponse) {
  manageCasesTarget.value = updated;
  loadTestRunsSilently();
}

// Refresh listing tanpa memunculkan "Memuat test run..." (yang akan
// menyembunyikan daftar & terasa berkedip saat modal masih terbuka).
async function loadTestRunsSilently() {
  try {
    testRuns.value = await testRunService.listTestRuns(projectId.value);
  } catch {
    // Diabaikan -- data lama tetap tampil, refresh berikutnya akan sinkron.
  }
}

// ---- Delete (requirement #6, permanen -- lihat DeleteTestRunModal.vue) ----
const isDeleteModalOpen = ref(false);
const deletingTestRun = ref<TestRunResponse | null>(null);

function openDeleteModal(testRun: TestRunResponse) {
  deletingTestRun.value = testRun;
  isDeleteModalOpen.value = true;
}

function closeDeleteModal() {
  isDeleteModalOpen.value = false;
  deletingTestRun.value = null;
}

function handleTestRunDeleted(testRunId: string) {
  testRuns.value = testRuns.value.filter((tr) => tr.id !== testRunId);
  closeDeleteModal();
}

// ---- Execute -- navigasi ke halaman detail utk catat hasil eksekusi tiap
// test case (status/comment/evidence, requirement #5) ----
function goToExecute(testRunId: string) {
  router.push({ name: 'project-test-run-execute', params: { projectId: projectId.value, testRunId } });
}

// ---- Display helpers ----
const STATUS_BADGE_CLASS: Record<TestRunStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  RUNNING: 'bg-blue-50 text-blue-600',
  FINISHED: 'bg-emerald-50 text-emerald-700',
};
const STATUS_LABEL: Record<TestRunStatus, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  FINISHED: 'Finished',
};

function progressSegments(testRun: TestRunResponse) {
  const total = testRun.testCaseCount;
  if (total === 0) return [];
  const rest = Math.max(total - testRun.passedCount - testRun.failedCount - testRun.blockedCount, 0);
  return [
    { key: 'passed', count: testRun.passedCount, class: 'bg-emerald-500' },
    { key: 'failed', count: testRun.failedCount, class: 'bg-red-500' },
    { key: 'blocked', count: testRun.blockedCount, class: 'bg-amber-500' },
    { key: 'rest', count: rest, class: 'bg-gray-200' },
  ].filter((segment) => segment.count > 0);
}
</script>

<template>
  <div class="px-8 py-8">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Test Runs</h1>
        <p class="mt-1 text-sm text-gray-400">Manage and execute your test runs</p>
      </div>
      <button
        v-if="isOwner"
        type="button"
        class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
        @click="isCreateModalOpen = true"
      >
        <PlusIcon :size="16" />
        New Test Run
      </button>
    </div>

    <p v-if="isLoading" class="mt-10 text-sm text-gray-400">Memuat test run...</p>
    <p v-else-if="loadError" class="mt-10 text-sm text-red-500">{{ loadError }}</p>

    <div v-else-if="testRuns.length === 0" class="mt-24 flex flex-col items-center justify-center text-center">
      <p class="text-gray-400">No test runs yet.</p>
      <button
        v-if="isOwner"
        type="button"
        class="mt-4 flex items-center gap-2 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        @click="isCreateModalOpen = true"
      >
        <PlusIcon :size="16" />
        Create your first run
      </button>
    </div>

    <div v-else class="mt-6 space-y-4">
      <div v-for="testRun in testRuns" :key="testRun.id" class="rounded-2xl border border-gray-100 p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-base font-bold text-gray-900">{{ testRun.title }}</h2>
            <div class="mt-1.5 flex items-center gap-2 text-sm text-gray-400">
              <span class="rounded-full px-2.5 py-1 text-xs font-medium" :class="STATUS_BADGE_CLASS[testRun.status]">
                {{ STATUS_LABEL[testRun.status] }}
              </span>
              <span>{{ testRun.testCaseCount }} case{{ testRun.testCaseCount === 1 ? '' : 's' }}</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button
              type="button"
              class="flex items-center gap-2 rounded-xl border border-gray-200 px-3.5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              @click="openManageCases(testRun.id)"
            >
              <SlidersIcon :size="15" />
              Manage Cases
            </button>
            <button
              v-if="isOwner"
              type="button"
              class="flex items-center gap-2 rounded-xl border border-gray-200 px-3.5 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
              @click="openDeleteModal(testRun)"
            >
              <TrashIcon :size="15" />
              Delete
            </button>
            <button
              type="button"
              class="flex items-center gap-2 rounded-xl bg-gray-900 px-3.5 py-2 text-sm font-medium text-white hover:bg-gray-800"
              @click="goToExecute(testRun.id)"
            >
              <PlayIcon :size="15" />
              Execute
            </button>
          </div>
        </div>

        <div class="mt-4 flex h-1.5 overflow-hidden rounded-full bg-gray-100">
          <div
            v-for="segment in progressSegments(testRun)"
            :key="segment.key"
            class="h-full"
            :class="segment.class"
            :style="{ width: `${(segment.count / testRun.testCaseCount) * 100}%` }"
          />
        </div>

        <div class="mt-2.5 flex items-center gap-4 text-sm">
          <span class="font-medium text-emerald-600">{{ testRun.passedCount }} passed</span>
          <span class="font-medium text-red-500">{{ testRun.failedCount }} failed</span>
          <span class="font-medium text-amber-500">{{ testRun.blockedCount }} blocked</span>
        </div>
      </div>
    </div>
  </div>

  <CreateTestRunModal
    :open="isCreateModalOpen"
    :project-id="projectId"
    @close="isCreateModalOpen = false"
    @created="handleTestRunCreated"
  />

  <ManageTestRunCasesModal
    v-if="!isLoadingManageCasesTarget"
    :open="isManageCasesModalOpen"
    :project-id="projectId"
    :test-run="manageCasesTarget"
    @close="closeManageCases"
    @updated="handleCasesUpdated"
  />

  <DeleteTestRunModal
    :open="isDeleteModalOpen"
    :project-id="projectId"
    :test-run="deletingTestRun"
    @close="closeDeleteModal"
    @deleted="handleTestRunDeleted"
  />
</template>

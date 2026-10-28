<!-- frontend/src/modules/testrun/views/TestRunExecuteView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import { testRunService } from '../services/testRun.service';
import type {
  TestRunDetailResponse,
  TestResultResponse,
  TestResultStatus,
  TestStepResultStatus,
} from '../types/testRun.types';
import { parseSteps } from '@/modules/testcase';
import EvidenceViewerModal from '../components/EvidenceViewerModal.vue';
import { EVIDENCE_ACCEPT, EVIDENCE_FORMAT_LABEL, evidenceKindOf } from '../utils/evidenceTypes';
import ArrowLeftIcon from '@/shared/components/icons/ArrowLeftIcon.vue';
import UploadIcon from '@/shared/components/icons/UploadIcon.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import CircleCheckIcon from '@/shared/components/icons/CircleCheckIcon.vue';
import XCircleIcon from '@/shared/components/icons/XCircleIcon.vue';

// Halaman tujuan tombol "Execute" di TestRunsView.vue (mengikuti mockup):
// kiri = daftar test case di run ini (titik warna = status), kanan = detail
// test case terpilih -- pre-conditions, test steps dengan tombol centang /
// silang PER STEP, comment/actual result, dan upload evidence (image/video).
// Status hasil test case ikut diturunkan otomatis dari tanda per-step; status
// test_run induk (PENDING/RUNNING/FINISHED) dihitung backend
// (TestRunServiceImpl.recomputeRunStatus()).
const route = useRoute();
const router = useRouter();
const projectId = computed(() => route.params.projectId as string);
const testRunId = computed(() => route.params.testRunId as string);

const testRun = ref<TestRunDetailResponse | null>(null);
const isLoading = ref(true);
const loadError = ref('');
const selectedResultId = ref<string | null>(null);

// silent = true dipakai utk refetch SETELAH aksi (simpan, upload): halaman
// TIDAK boleh balik ke "Memuat..." karena itu meng-unmount seluruh panel.
async function loadTestRun(options: { silent?: boolean } = {}) {
  const silent = options.silent === true;
  if (!silent) {
    isLoading.value = true;
    loadError.value = '';
  }
  try {
    testRun.value = await testRunService.getTestRunDetail(projectId.value, testRunId.value);
    staleResultIds.clear(); // data & versi baru dari server
    const results = testRun.value.testResults;
    if (!results.some((r) => r.id === selectedResultId.value)) {
      selectedResultId.value = results[0]?.id ?? null;
    }
  } catch {
    if (!silent) {
      loadError.value = 'Test run tidak ditemukan.';
    }
  } finally {
    if (!silent) {
      isLoading.value = false;
    }
  }
}

onMounted(() => loadTestRun());

const results = computed<TestResultResponse[]>(() => testRun.value?.testResults ?? []);
const selectedResult = computed<TestResultResponse | null>(
  () => results.value.find((r) => r.id === selectedResultId.value) ?? null
);

// ---- Progress header: "X/Y Completed (Z%)" ----
// "Completed" = sudah punya kesimpulan (PASSED / FAILED / BLOCKED); NEW dan
// PENDING masih dianggap belum selesai.
const COMPLETED_STATUSES: TestResultStatus[] = ['PASSED', 'FAILED', 'BLOCKED'];
const completedCount = computed(() => results.value.filter((r) => COMPLETED_STATUSES.includes(r.status)).length);
const progressPercent = computed(() =>
  results.value.length === 0 ? 0 : Math.round((completedCount.value / results.value.length) * 100)
);

function goBack() {
  flushComment();
  router.push({ name: 'project-test-runs', params: { projectId: projectId.value } });
}

// ---- Tampilan status ----
const STATUS_OPTIONS: TestResultStatus[] = ['NEW', 'PASSED', 'FAILED', 'PENDING', 'BLOCKED'];
const STATUS_DOT_CLASS: Record<TestResultStatus, string> = {
  NEW: 'bg-gray-300',
  PASSED: 'bg-emerald-500',
  FAILED: 'bg-red-500',
  PENDING: 'bg-amber-400',
  BLOCKED: 'bg-purple-500',
};

// ---- Test steps (dari test case, format teks "1. ...\n2. ...") ----
function stepRowsOf(result: TestResultResponse) {
  const rows = parseSteps(result.testCaseTestStep, result.testCaseExpectedResult);
  // parseSteps() mengembalikan 1 baris kosong kalau test case tidak punya step.
  const hasAnyStep = rows.some((row) => row.action || row.expectedResult);
  return hasAnyStep ? rows : [];
}
const stepRows = computed(() => (selectedResult.value ? stepRowsOf(selectedResult.value) : []));

function stepStatusesOf(result: TestResultResponse, stepCount: number): TestStepResultStatus[] {
  // Dipad/dipotong ke jumlah step SAAT INI (test case bisa diedit setelah run dibuat).
  return Array.from({ length: stepCount }, (_, i) => result.stepResults[i] ?? 'NEW');
}

// Status test case diturunkan dari tanda per-step: ada yang FAILED -> FAILED,
// semua PASSED -> PASSED, sebagian PASSED -> PENDING (sedang berjalan),
// belum ada yang ditandai -> NEW.
function deriveStatus(steps: TestStepResultStatus[]): TestResultStatus {
  if (steps.some((s) => s === 'FAILED')) return 'FAILED';
  if (steps.every((s) => s === 'PASSED')) return 'PASSED';
  if (steps.some((s) => s === 'PASSED')) return 'PENDING';
  return 'NEW';
}

// ---- Simpan: antrean berurutan + update optimistis ----
// Klik cepat beruntun / blur textarea lalu klik step TIDAK BOLEH saling
// menimpa (request terakhir yang tiba di server = yang menang), jadi semua
// PATCH dijalankan satu per satu. Refetch penuh baru dilakukan setelah
// antrean kosong supaya state optimistis tidak ditimpa data lama.
const savingCount = ref(0);
const saveError = ref('');
let saveChain: Promise<void> = Promise.resolve();

// Test result yang barusan ditolak backend dgn 409 (tester lain menyimpan
// lebih dulu): sisa simpanan antrean utk result itu DILEWATI -- semuanya pasti
// ditolak lagi karena versinya sudah usang -- sampai data dimuat ulang dari
// server (akhir antrean) dan versinya segar lagi.
const staleResultIds = new Set<string>();
const CONFLICT_MESSAGE =
  'Hasil test ini baru saja diubah pengguna lain. Data dimuat ulang -- ulangi perubahan kamu jika masih perlu.';

const commentDrafts = ref<Record<string, string>>({});
function commentOf(result: TestResultResponse): string {
  return commentDrafts.value[result.id] ?? result.comment ?? '';
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    return (err.response?.data as { message?: string })?.message ?? fallback;
  }
  return 'Tidak dapat terhubung ke server';
}

function queueSave(resultId: string) {
  saveError.value = '';
  savingCount.value++;
  saveChain = saveChain
    .then(async () => {
      const result = results.value.find((r) => r.id === resultId);
      if (!result || staleResultIds.has(resultId)) return;
      // Dihitung dari result itu sendiri (BUKAN dari test case yang sedang
      // dibuka) -- user bisa pindah test case sebelum antrean ini jalan.
      const rows = stepRowsOf(result);
      try {
        const saved = await testRunService.updateTestResult(projectId.value, testRunId.value, result.id, {
          status: result.status,
          comment: commentOf(result),
          // Tanpa test step -> tidak dikirim, supaya tanda step yang sudah
          // tersimpan tidak terhapus.
          stepResults: rows.length > 0 ? stepStatusesOf(result, rows.length) : undefined,
          // Versi yang terakhir kita lihat -- backend menolak (409) kalau
          // sudah berubah oleh orang lain.
          version: result.version,
        });
        // HANYA versi yang diambil dari balasan (status/step/comment tetap
        // state optimistis lokal) supaya simpanan berikutnya di antrean
        // membawa versi terbaru dan tidak bentrok dengan simpanan kita sendiri.
        result.version = saved.version;
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 409) {
          staleResultIds.add(resultId);
          saveError.value = CONFLICT_MESSAGE;
        } else {
          saveError.value = extractErrorMessage(err, 'Gagal menyimpan hasil, coba lagi');
        }
      }
    })
    .then(async () => {
      savingCount.value--;
      if (savingCount.value === 0) {
        await loadTestRun({ silent: true }); // sinkron ulang / kembalikan kalau ada yang gagal
      }
    });
}

function setStepStatus(index: number, value: 'PASSED' | 'FAILED') {
  const result = selectedResult.value;
  if (!result) return;
  const steps = stepStatusesOf(result, stepRows.value.length);
  // Klik tombol yang sama sekali lagi = batalkan tanda.
  steps[index] = steps[index] === value ? 'NEW' : value;
  result.stepResults = steps;
  result.status = deriveStatus(steps);
  queueSave(result.id);
}

function handleStatusChange(event: Event) {
  const result = selectedResult.value;
  if (!result) return;
  result.status = (event.target as HTMLSelectElement).value as TestResultStatus;
  queueSave(result.id);
}

function handleCommentInput(event: Event) {
  const result = selectedResult.value;
  if (!result) return;
  commentDrafts.value[result.id] = (event.target as HTMLTextAreaElement).value;
}

function flushComment() {
  const result = selectedResult.value;
  if (!result) return;
  const draft = commentOf(result);
  if (draft === (result.comment ?? '')) return; // tidak berubah, skip API call
  result.comment = draft;
  queueSave(result.id);
}

function selectResult(id: string) {
  if (id === selectedResultId.value) return;
  flushComment();
  saveError.value = '';
  evidenceError.value = '';
  selectedResultId.value = id;
}

// ---- Evidence (image/video, maks. 50MB -- lihat validateEvidenceFile backend) ----
const MAX_EVIDENCE_SIZE_BYTES = 50 * 1024 * 1024;
const fileInputRef = ref<HTMLInputElement | null>(null);
const isUploading = ref(false);
const isViewerOpen = ref(false);
const evidenceError = ref('');

function triggerFilePicker() {
  fileInputRef.value?.click();
}

async function handleFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = ''; // reset supaya bisa pilih file yang sama lagi kalau perlu re-upload
  const result = selectedResult.value;
  if (!file || !result) return;

  evidenceError.value = '';
  // Daftar sama dgn whitelist backend (png, jpg, gif, webp, mp4, mov, webm).
  // SVG/HTML sengaja ditolak -- bisa berisi skrip.
  if (evidenceKindOf(file.type) === null) {
    evidenceError.value = `Evidence hanya boleh berupa ${EVIDENCE_FORMAT_LABEL}.`;
    return;
  }
  if (file.size > MAX_EVIDENCE_SIZE_BYTES) {
    evidenceError.value = 'Ukuran file evidence maksimal 50MB.';
    return;
  }

  isUploading.value = true;
  try {
    // Tunggu simpanan status/comment yang masih antre selesai dulu -- upload
    // & update sama-sama menulis ke baris test_result yang sama.
    await saveChain;
    await testRunService.uploadEvidence(projectId.value, testRunId.value, result.id, file);
    await loadTestRun({ silent: true });
  } catch (err) {
    evidenceError.value = extractErrorMessage(err, 'Gagal upload evidence, coba lagi');
  } finally {
    isUploading.value = false;
  }
}

// Evidence dibuka di modal (EvidenceViewerModal) lewat <img>/<video> -- BUKAN
// window.open(blobUrl), karena blob URL satu origin dgn aplikasi dan file
// berbahaya bisa mengeksekusi skrip yang membaca JWT di localStorage.
function viewEvidence() {
  if (!selectedResult.value?.evidenceUrl) return;
  evidenceError.value = '';
  isViewerOpen.value = true;
}
</script>

<template>
  <div class="flex min-h-[calc(100vh-4.3rem)] flex-col">
    <!-- Header: Back + progress -->
    <div class="flex items-center gap-8 border-b border-gray-100 px-6 py-3">
      <button
        type="button"
        class="flex shrink-0 items-center gap-2 text-sm font-medium text-gray-900 hover:text-gray-600"
        @click="goBack"
      >
        <ArrowLeftIcon :size="15" />
        Back
      </button>

      <div v-if="testRun && results.length > 0" class="min-w-0 flex-1">
        <p class="flex items-baseline gap-2 text-sm font-semibold text-gray-900">
          {{ completedCount }}/{{ results.length }} Completed
          <span class="text-xs font-normal text-gray-400">({{ progressPercent }}%)</span>
          <span v-if="savingCount > 0" class="text-xs font-normal text-gray-400">Saving...</span>
        </p>
        <div class="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-gray-100">
          <div class="h-full rounded-full bg-gray-900 transition-all" :style="{ width: `${progressPercent}%` }" />
        </div>
      </div>
    </div>

    <p v-if="isLoading" class="px-8 py-8 text-sm text-gray-400">Memuat test run...</p>
    <p v-else-if="loadError || !testRun" class="px-8 py-8 text-sm text-red-500">{{ loadError }}</p>

    <div v-else-if="results.length === 0" class="flex flex-1 flex-col items-center justify-center text-center text-gray-400">
      <p>Belum ada test case di run ini.</p>
      <p class="mt-1 text-sm">Kembali ke Test Runs lalu pakai "Manage Cases" untuk menambahkannya.</p>
    </div>

    <div v-else class="flex flex-1">
      <!-- Kiri: daftar test case -->
      <aside class="w-72 shrink-0 border-r border-gray-100 bg-white">
        <p class="border-b border-gray-100 px-5 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">
          Test Cases
        </p>
        <ul class="space-y-1 p-2">
          <li v-for="result in results" :key="result.id">
            <button
              type="button"
              class="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-gray-900 transition-colors"
              :class="result.id === selectedResultId ? 'bg-gray-100' : 'hover:bg-gray-50'"
              @click="selectResult(result.id)"
            >
              <span class="h-2.5 w-2.5 shrink-0 rounded-full" :class="STATUS_DOT_CLASS[result.status]" :title="result.status" />
              <span class="truncate">{{ result.testCaseTitle }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <!-- Kanan: detail test case terpilih -->
      <section v-if="selectedResult" class="min-w-0 flex-1 bg-gray-50/60 px-8 py-8">
        <div class="max-w-[44rem]">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <h1 class="text-xl font-semibold text-gray-900">{{ selectedResult.testCaseTitle }}</h1>
              <p v-if="selectedResult.testCaseDescription" class="mt-1 whitespace-pre-line text-sm text-gray-400">
                {{ selectedResult.testCaseDescription }}
              </p>
            </div>
            <label class="flex shrink-0 items-center gap-2 text-xs font-semibold uppercase tracking-wide text-gray-500">
              Status
              <select
                :value="selectedResult.status"
                class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-sm font-medium normal-case tracking-normal text-gray-700 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
                @change="handleStatusChange"
              >
                <option v-for="opt in STATUS_OPTIONS" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </label>
          </div>

          <div v-if="selectedResult.testCasePrecondition" class="mt-6">
            <h2 class="text-xs font-semibold uppercase tracking-wide text-gray-500">Pre-conditions</h2>
            <p class="mt-2 whitespace-pre-line rounded-xl bg-gray-100 px-4 py-3 text-sm text-gray-900">
              {{ selectedResult.testCasePrecondition }}
            </p>
          </div>

          <div class="mt-6">
            <h2 class="text-xs font-semibold uppercase tracking-wide text-gray-500">Test Steps</h2>
            <p v-if="stepRows.length === 0" class="mt-2 rounded-xl border border-dashed border-gray-200 px-4 py-4 text-sm text-gray-400">
              Test case ini belum punya test step. Tentukan hasilnya lewat pilihan Status di atas.
            </p>
            <ul v-else class="mt-2 space-y-2">
              <li
                v-for="(row, index) in stepRows"
                :key="index"
                class="flex items-center justify-between gap-4 rounded-xl border border-gray-100 bg-white px-4 py-3"
              >
                <div class="min-w-0">
                  <p class="text-sm text-gray-900">
                    <span class="mr-1.5 text-gray-400">#{{ index + 1 }}</span>
                    <span class="font-medium">{{ row.action }}</span>
                  </p>
                  <p v-if="row.expectedResult" class="mt-0.5 text-xs text-gray-500">Expected: {{ row.expectedResult }}</p>
                </div>
                <div class="flex shrink-0 items-center gap-3">
                  <button
                    type="button"
                    title="Passed"
                    aria-label="Tandai step passed"
                    class="rounded-full p-0.5 transition-colors"
                    :class="
                      stepStatusesOf(selectedResult, stepRows.length)[index] === 'PASSED'
                        ? 'bg-emerald-50 text-emerald-600'
                        : 'text-gray-800 hover:text-emerald-600'
                    "
                    @click="setStepStatus(index, 'PASSED')"
                  >
                    <CircleCheckIcon :size="18" />
                  </button>
                  <button
                    type="button"
                    title="Failed"
                    aria-label="Tandai step failed"
                    class="rounded-full p-0.5 transition-colors"
                    :class="
                      stepStatusesOf(selectedResult, stepRows.length)[index] === 'FAILED'
                        ? 'bg-red-50 text-red-600'
                        : 'text-gray-800 hover:text-red-600'
                    "
                    @click="setStepStatus(index, 'FAILED')"
                  >
                    <XCircleIcon :size="18" />
                  </button>
                </div>
              </li>
            </ul>
          </div>

          <div class="mt-6">
            <h2 class="text-xs font-semibold uppercase tracking-wide text-gray-500">Comment / Actual Result</h2>
            <textarea
              :value="commentOf(selectedResult)"
              rows="4"
              placeholder="Enter actual result or notes..."
              class="mt-2 w-full resize-y rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              @input="handleCommentInput"
              @blur="flushComment"
            />
            <p v-if="saveError" class="mt-2 text-xs text-red-600">{{ saveError }}</p>
          </div>

          <div class="mt-6">
            <h2 class="text-xs font-semibold uppercase tracking-wide text-gray-500">Evidence</h2>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <input
                ref="fileInputRef"
                type="file"
                :accept="EVIDENCE_ACCEPT"
                class="hidden"
                @change="handleFileSelected"
              />
              <button
                type="button"
                :disabled="isUploading"
                class="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                @click="triggerFilePicker"
              >
                <UploadIcon :size="15" />
                {{ isUploading ? 'Uploading...' : selectedResult.evidenceUrl ? 'Replace Evidence' : 'Upload Evidence' }}
              </button>
              <button
                v-if="selectedResult.evidenceUrl"
                type="button"
                class="flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                @click="viewEvidence"
              >
                <EyeIcon :size="15" />
                View
              </button>
              <span class="text-xs text-gray-400">
                {{ selectedResult.evidenceUrl ? 'Evidence terlampir · ' : '' }}{{ EVIDENCE_FORMAT_LABEL }}, maks. 50MB
              </span>
            </div>
            <p v-if="evidenceError" class="mt-2 text-xs text-red-600">{{ evidenceError }}</p>
          </div>
        </div>
      </section>
    </div>

    <EvidenceViewerModal
      v-if="isViewerOpen && selectedResult?.evidenceUrl"
      :evidence-url="selectedResult.evidenceUrl"
      :title="selectedResult.testCaseTitle"
      @close="isViewerOpen = false"
    />
  </div>
</template>

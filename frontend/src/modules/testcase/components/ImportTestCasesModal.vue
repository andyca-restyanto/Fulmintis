<!-- frontend/src/modules/testcase/components/ImportTestCasesModal.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import axios from 'axios';
import { testCaseService } from '../services/testCase.service';
import type { TestCaseImportResult } from '../types/testCaseImport.types';
import {
  IMPORT_MAX_FILE_BYTES,
  IMPORT_MAX_ROWS,
  canConfirmImport,
  extractImportFailure,
  fieldLabel,
  formatFileSize,
  validateImportFile,
} from '../utils/testCaseImportUi';
import {
  priorityBadgeClass,
  priorityLabel,
  scenarioBadgeClass,
  scenarioLabel,
  typeLabel,
} from '../utils/testCaseDisplay';
import XIcon from '@/shared/components/icons/XIcon.vue';
import UploadIcon from '@/shared/components/icons/UploadIcon.vue';
import DownloadIcon from '@/shared/components/icons/DownloadIcon.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';

// Import test case dari Excel, 3 langkah:
//   1. Upload  -- unduh template, pilih/seret file .xlsx
//   2. Review  -- pratinjau (dryRun=true): pemetaan kolom, error per baris, peringatan, contoh baris
//   3. Import  -- simpan (dryRun=false); SEMUA baris valid disimpan atau tidak sama sekali
// Semua test case masuk ke folder yang sedang dibuka. Parent (TestCasePanel) yang
// menutup modal, menampilkan notifikasi sukses, dan memuat ulang daftar lewat event `imported`.
const props = defineProps<{
  open: boolean;
  projectId: string;
  folderId: string;
  folderName: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'imported', result: TestCaseImportResult): void;
}>();

type Step = 'select' | 'review';
const step = ref<Step>('select');
const file = ref<File | null>(null);
const result = ref<TestCaseImportResult | null>(null);
const fileError = ref('');
const generalError = ref('');
const isDragging = ref(false);
const isDownloadingTemplate = ref(false);
const isChecking = ref(false);
const isImporting = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const isBusy = computed(() => isChecking.value || isImporting.value);
const canConfirm = computed(() => canConfirmImport(result.value));
// 0 = Upload, 1 = Review, 2 = Import (sedang menyimpan)
const activeStepIndex = computed(() => (isImporting.value ? 2 : step.value === 'review' ? 1 : 0));
const STEPS = ['Upload file', 'Review', 'Import'];

function reset() {
  step.value = 'select';
  file.value = null;
  result.value = null;
  fileError.value = '';
  generalError.value = '';
  isDragging.value = false;
  if (fileInput.value) fileInput.value.value = '';
}

// Dibuka lagi -> selalu mulai dari langkah 1 dengan keadaan bersih.
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) reset();
  }
);

function handleClose() {
  if (isBusy.value) return; // jangan menutup di tengah pemeriksaan/penyimpanan
  emit('close');
}

// ---- Langkah 1: pilih file ----
function chooseFile(candidate: File | null | undefined) {
  generalError.value = '';
  if (!candidate) return;
  const problem = validateImportFile(candidate);
  if (problem) {
    file.value = null;
    fileError.value = problem;
    return;
  }
  fileError.value = '';
  file.value = candidate;
}

function handleFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement;
  chooseFile(input.files?.[0]);
  input.value = ''; // pilih file yang sama dua kali tetap memicu change
}

function handleDrop(event: DragEvent) {
  isDragging.value = false;
  if (isBusy.value) return;
  chooseFile(event.dataTransfer?.files?.[0]); // lebih dari satu file -> hanya yang pertama
}

function clearFile() {
  file.value = null;
  fileError.value = '';
}

async function downloadTemplate() {
  if (isDownloadingTemplate.value) return;
  isDownloadingTemplate.value = true;
  generalError.value = '';
  try {
    const blob = await testCaseService.downloadImportTemplate(props.projectId);
    // Unduhan lewat blob (bukan <a href> biasa) karena endpoint-nya butuh header JWT.
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'test-case-import-template.xlsx';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch {
    generalError.value = 'Gagal mengunduh template, coba lagi.';
  } finally {
    isDownloadingTemplate.value = false;
  }
}

function describeFailure(err: unknown, fallback: string) {
  if (axios.isAxiosError(err)) {
    return extractImportFailure(err.response?.status, err.response?.data, fallback);
  }
  return { kind: 'message' as const, message: 'Tidak dapat terhubung ke server.' };
}

// ---- Langkah 1 -> 2: pratinjau (dryRun=true, TIDAK menyimpan apa pun) ----
async function checkFile() {
  if (!file.value || isBusy.value) return;
  isChecking.value = true;
  generalError.value = '';
  try {
    result.value = await testCaseService.importTestCases(props.projectId, props.folderId, file.value, true);
    step.value = 'review';
  } catch (err) {
    const failure = describeFailure(err, 'Gagal memeriksa file, coba lagi.');
    if (failure.kind === 'result') {
      result.value = failure.result;
      step.value = 'review';
    } else {
      generalError.value = failure.message; // mis. kolom wajib tidak ada, bukan .xlsx, > 500 baris
    }
  } finally {
    isChecking.value = false;
  }
}

function backToSelect() {
  if (isBusy.value) return;
  reset();
}

// ---- Langkah 3: simpan (dryRun=false) ----
async function confirmImport() {
  if (!file.value || !canConfirm.value || isBusy.value) return;
  isImporting.value = true;
  generalError.value = '';
  try {
    const saved = await testCaseService.importTestCases(props.projectId, props.folderId, file.value, false);
    emit('imported', saved); // parent menutup modal + notifikasi + muat ulang daftar
  } catch (err) {
    const failure = describeFailure(err, 'Import gagal, coba lagi.');
    if (failure.kind === 'result') {
      // Berubah sejak pratinjau (mis. file diganti) -> tampilkan daftar error terbaru. Tidak ada yang tersimpan.
      result.value = failure.result;
      generalError.value = 'Import dibatalkan karena ada baris yang tidak valid. Tidak ada test case yang disimpan.';
    } else {
      generalError.value = failure.message;
    }
  } finally {
    isImporting.value = false;
  }
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div
      class="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-2xl bg-white shadow-xl"
      role="dialog"
      aria-modal="true"
      aria-labelledby="import-test-cases-title"
    >
      <!-- Header -->
      <div class="flex items-start justify-between border-b border-gray-100 px-6 py-5">
        <div>
          <h3 id="import-test-cases-title" class="text-lg font-bold text-gray-900">Import Test Cases</h3>
          <p class="mt-0.5 text-sm text-gray-500">
            Dari Excel ke folder <span class="font-semibold text-gray-700">{{ folderName }}</span>
          </p>
        </div>
        <button
          type="button"
          class="text-gray-400 hover:text-gray-600 disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="isBusy"
          aria-label="Tutup"
          @click="handleClose"
        >
          <XIcon :size="20" />
        </button>
      </div>

      <!-- Indikator langkah -->
      <ol class="flex items-center gap-2 border-b border-gray-100 px-6 py-3 text-xs font-medium">
        <template v-for="(label, index) in STEPS" :key="label">
          <li
            class="flex items-center gap-2"
            :class="index <= activeStepIndex ? 'text-gray-900' : 'text-gray-400'"
            :aria-current="index === activeStepIndex ? 'step' : undefined"
          >
            <span
              class="flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold"
              :class="index <= activeStepIndex ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-400'"
            >
              {{ index + 1 }}
            </span>
            {{ label }}
          </li>
          <li v-if="index < STEPS.length - 1" class="h-px w-6 bg-gray-200" aria-hidden="true" />
        </template>
      </ol>

      <!-- Isi -->
      <div class="flex-1 overflow-y-auto px-6 py-5">
        <!-- ===== Langkah 1: Upload ===== -->
        <div v-if="step === 'select'" class="space-y-4">
          <div class="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-gray-50 px-4 py-3">
            <p class="text-sm text-gray-600">
              Gunakan template agar kolom dan nilainya sesuai. ID test case dibuat otomatis oleh sistem.
            </p>
            <button
              type="button"
              class="flex shrink-0 items-center gap-2 rounded-xl border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isDownloadingTemplate"
              @click="downloadTemplate"
            >
              <DownloadIcon :size="15" />
              {{ isDownloadingTemplate ? 'Mengunduh...' : 'Download Template' }}
            </button>
          </div>

          <label
            class="flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors"
            :class="isDragging ? 'border-gray-900 bg-gray-50' : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleDrop"
          >
            <input
              ref="fileInput"
              type="file"
              class="sr-only"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              @change="handleFileInputChange"
            />
            <UploadIcon :size="28" class="mb-2 text-gray-400" />
            <template v-if="file">
              <p class="text-sm font-semibold text-gray-900">{{ file.name }}</p>
              <p class="mt-0.5 text-xs text-gray-400">{{ formatFileSize(file.size) }}</p>
              <button
                type="button"
                class="mt-3 text-xs font-medium text-gray-500 underline-offset-2 hover:text-gray-800 hover:underline"
                @click.prevent="clearFile"
              >
                Ganti file
              </button>
            </template>
            <template v-else>
              <p class="text-sm font-medium text-gray-700">Seret file .xlsx ke sini, atau klik untuk memilih</p>
              <p class="mt-1 text-xs text-gray-400">
                Maks. {{ formatFileSize(IMPORT_MAX_FILE_BYTES) }} dan {{ IMPORT_MAX_ROWS }} baris &middot; kolom wajib: Title,
                Priority, Test type
              </p>
            </template>
          </label>

          <p v-if="fileError" class="flex items-start gap-2 text-sm text-red-600" role="alert">
            <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ fileError }}
          </p>
          <p v-if="generalError" class="flex items-start gap-2 text-sm text-red-600" role="alert">
            <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ generalError }}
          </p>
        </div>

        <!-- ===== Langkah 2/3: Review & Import ===== -->
        <div v-else-if="result" class="space-y-5">
          <!-- Ringkasan -->
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div class="rounded-xl border border-gray-200 px-4 py-3">
              <p class="text-xs text-gray-500">Total rows</p>
              <p class="mt-1 text-xl font-bold text-gray-900">{{ result.totalRows }}</p>
            </div>
            <div class="rounded-xl border border-gray-200 px-4 py-3">
              <p class="text-xs text-gray-500">Valid</p>
              <p class="mt-1 text-xl font-bold text-emerald-600">{{ result.validRows }}</p>
            </div>
            <div class="rounded-xl border px-4 py-3" :class="result.errors.length ? 'border-red-200 bg-red-50' : 'border-gray-200'">
              <p class="text-xs text-gray-500">Errors</p>
              <p class="mt-1 text-xl font-bold" :class="result.errors.length ? 'text-red-600' : 'text-gray-900'">
                {{ result.errors.length }}
              </p>
            </div>
            <div class="rounded-xl border px-4 py-3" :class="result.warnings.length ? 'border-amber-200 bg-amber-50' : 'border-gray-200'">
              <p class="text-xs text-gray-500">Warnings</p>
              <p class="mt-1 text-xl font-bold" :class="result.warnings.length ? 'text-amber-600' : 'text-gray-900'">
                {{ result.warnings.length }}
              </p>
            </div>
          </div>

          <!-- Error: memblokir import -->
          <section v-if="result.errors.length > 0" class="rounded-xl border border-red-200 bg-red-50/50" role="alert">
            <p class="px-4 pt-3 text-sm font-semibold text-red-700">
              Perbaiki {{ result.errors.length }} kesalahan di file lalu unggah ulang. Import hanya berjalan kalau semua
              baris valid.
            </p>
            <div class="max-h-56 overflow-auto px-4 pb-3 pt-2">
              <table class="w-full text-left text-xs">
                <thead class="text-red-500">
                  <tr>
                    <th class="w-16 py-1 font-medium">Baris</th>
                    <th class="w-32 py-1 font-medium">Kolom</th>
                    <th class="py-1 font-medium">Masalah</th>
                  </tr>
                </thead>
                <tbody class="text-gray-700">
                  <tr v-for="(issue, i) in result.errors" :key="`e-${i}`" class="border-t border-red-100 align-top">
                    <td class="py-1.5 font-semibold">{{ issue.row }}</td>
                    <td class="py-1.5">{{ issue.column }}</td>
                    <td class="py-1.5">{{ issue.message }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <!-- Peringatan: tidak memblokir -->
          <section v-if="result.warnings.length > 0" class="rounded-xl border border-amber-200 bg-amber-50/50">
            <p class="px-4 pt-3 text-sm font-semibold text-amber-700">Peringatan (import tetap bisa dilanjutkan)</p>
            <div class="max-h-40 overflow-auto px-4 pb-3 pt-2">
              <table class="w-full text-left text-xs">
                <tbody class="text-gray-700">
                  <tr v-for="(issue, i) in result.warnings" :key="`w-${i}`" class="border-t border-amber-100 align-top first:border-0">
                    <td class="w-16 py-1.5 font-semibold">{{ issue.row }}</td>
                    <td class="w-32 py-1.5">{{ issue.column }}</td>
                    <td class="py-1.5">{{ issue.message }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <!-- Pemetaan kolom (auto mapping) -->
          <section>
            <h4 class="mb-2 text-sm font-semibold text-gray-900">Pemetaan kolom</h4>
            <ul class="flex flex-wrap gap-2">
              <li
                v-for="mapping in result.columnMapping"
                :key="mapping.excelColumn"
                class="flex items-center gap-1.5 rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-600"
              >
                {{ mapping.excelColumn }}
                <span class="text-gray-300" aria-hidden="true">&rarr;</span>
                <span class="font-semibold text-gray-900">{{ fieldLabel(mapping.field) }}</span>
              </li>
            </ul>
            <p v-if="result.unmappedColumns.length > 0" class="mt-2 text-xs text-gray-400">
              Kolom tidak dikenali dan diabaikan: {{ result.unmappedColumns.join(', ') }}
            </p>
          </section>

          <!-- Pratinjau baris valid -->
          <section v-if="result.preview.length > 0">
            <h4 class="mb-2 text-sm font-semibold text-gray-900">
              Pratinjau
              <span class="font-normal text-gray-400">
                ({{ result.preview.length }}<template v-if="result.validRows > result.preview.length"> dari {{ result.validRows }}</template>
                baris valid)
              </span>
            </h4>
            <div class="overflow-x-auto rounded-xl border border-gray-200">
              <table class="w-full text-left text-xs">
                <thead>
                  <tr class="border-b border-gray-100 uppercase tracking-wide text-gray-400">
                    <th class="px-3 py-2 font-medium">Baris</th>
                    <th class="px-3 py-2 font-medium">Title</th>
                    <th class="px-3 py-2 font-medium">Priority</th>
                    <th class="px-3 py-2 font-medium">Type</th>
                    <th class="px-3 py-2 font-medium">Scenario</th>
                    <th class="px-3 py-2 text-right font-medium">Steps</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in result.preview" :key="row.row" class="border-b border-gray-50 last:border-0">
                    <td class="px-3 py-2 text-gray-400">{{ row.row }}</td>
                    <td class="max-w-[16rem] truncate px-3 py-2 font-medium text-gray-900" :title="row.title">{{ row.title }}</td>
                    <td class="px-3 py-2">
                      <span class="rounded-full px-2 py-0.5 font-medium" :class="priorityBadgeClass(row.priority)">
                        {{ priorityLabel(row.priority) }}
                      </span>
                    </td>
                    <td class="px-3 py-2 text-gray-600">{{ typeLabel(row.type) }}</td>
                    <td class="px-3 py-2">
                      <span class="rounded-full px-2 py-0.5" :class="scenarioBadgeClass(row.scenarioType)">
                        {{ scenarioLabel(row.scenarioType) }}
                      </span>
                    </td>
                    <td class="px-3 py-2 text-right text-gray-600">{{ row.stepCount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <p v-if="isImporting" class="text-sm text-gray-500" aria-live="polite">
            Mengimpor {{ result.validRows }} test case... jangan tutup jendela ini.
          </p>
          <p v-if="generalError" class="flex items-start gap-2 text-sm text-red-600" role="alert">
            <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ generalError }}
          </p>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex justify-end gap-3 border-t border-gray-100 px-6 py-4">
        <button
          type="button"
          class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="isBusy"
          @click="handleClose"
        >
          Cancel
        </button>

        <button
          v-if="step === 'review'"
          type="button"
          class="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="isBusy"
          @click="backToSelect"
        >
          Pilih file lain
        </button>

        <button
          v-if="step === 'select'"
          type="button"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
          :disabled="!file || isBusy"
          @click="checkFile"
        >
          {{ isChecking ? 'Memeriksa...' : 'Check file' }}
        </button>
        <button
          v-else
          type="button"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
          :disabled="!canConfirm || isBusy"
          @click="confirmImport"
        >
          {{ isImporting ? 'Mengimpor...' : result ? `Import ${result.validRows} test case` : 'Import' }}
        </button>
      </div>
    </div>
  </div>
</template>

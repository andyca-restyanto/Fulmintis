<!-- frontend/src/modules/testrun/components/ManageTestRunCasesModal.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import axios from 'axios';
import { testRunService } from '../services/testRun.service';
import type { TestResultResponse, TestRunDetailResponse } from '../types/testRun.types';
import TestCaseSelector from './TestCaseSelector.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';

// Manage test case di test run yang sudah ada -- 1 checklist (mengikuti
// mockup): test case yang SUDAH ada di run tampil TERCENTANG. Uncheck =
// lepas dari run, check test case lain = tambah ke run, lalu "Save Changes".
// Boleh OWNER MAUPUN COLLABORATOR. Semua perubahan dikirim sekaligus lewat
// PUT .../test-runs/{id}/test-cases (daftar akhir, 1 transaksi di backend
// Luhut -- lihat TestRunServiceImpl.syncTestCasesInRun()).
const props = defineProps<{
  open: boolean;
  projectId: string;
  testRun: TestRunDetailResponse | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  // Dikirim setelah Save berhasil; modal langsung ditutup, parent cukup
  // refresh listing (jumlah case, progress bar, status run).
  (e: 'updated', testRun: TestRunDetailResponse): void;
}>();

const selectedTestCaseIds = ref<string[]>([]);
const isSaving = ref(false);
const generalError = ref('');
const isConfirmingRemoval = ref(false);

const mappedResults = computed<TestResultResponse[]>(() => props.testRun?.testResults ?? []);
const mappedIds = computed(() => new Set(mappedResults.value.map((r) => r.testCaseId)));
const selectedSet = computed(() => new Set(selectedTestCaseIds.value));

const removedResults = computed(() => mappedResults.value.filter((r) => !selectedSet.value.has(r.testCaseId)));
const hasChanges = computed(
  () => removedResults.value.length > 0 || selectedTestCaseIds.value.some((id) => !mappedIds.value.has(id))
);

// Test case yang mau dilepas TAPI sudah punya hasil eksekusi/evidence --
// hilang permanen, jadi butuh konfirmasi sebelum Save.
const removedWithData = computed(() =>
  removedResults.value.filter((r) => r.status !== 'NEW' || r.evidenceUrl)
);

// immediate: parent me-mount modal ini SETELAH detail run selesai di-fetch
// (open sudah true & testRun sudah terisi), jadi watch tanpa immediate
// tidak akan pernah jalan dan checklist awalnya kosong.
watch(
  () => [props.open, props.testRun?.id] as const,
  ([isOpen]) => {
    if (isOpen) {
      selectedTestCaseIds.value = mappedResults.value.map((r) => r.testCaseId);
      generalError.value = '';
      isConfirmingRemoval.value = false;
    }
  },
  { immediate: true }
);

// Kalau user mengubah checklist lagi, konfirmasi sebelumnya batal.
watch(selectedTestCaseIds, () => {
  isConfirmingRemoval.value = false;
});

function handleClose() {
  if (isSaving.value) return;
  emit('close');
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    return data?.message ?? fallback;
  }
  return 'Tidak dapat terhubung ke server';
}

function handleSaveClick() {
  if (isSaving.value) return;
  // Tidak ada perubahan -> cukup tutup, tidak perlu API call.
  if (!hasChanges.value) {
    emit('close');
    return;
  }
  if (removedWithData.value.length > 0 && !isConfirmingRemoval.value) {
    isConfirmingRemoval.value = true;
    return;
  }
  save();
}

async function save() {
  if (!props.testRun) return;

  generalError.value = '';
  isSaving.value = true;
  try {
    const updated = await testRunService.syncTestCasesInRun(props.projectId, props.testRun.id, {
      testCaseIds: selectedTestCaseIds.value,
    });
    emit('updated', updated);
    emit('close');
  } catch (err) {
    generalError.value = extractErrorMessage(err, 'Gagal menyimpan perubahan, coba lagi');
    isConfirmingRemoval.value = false;
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div
    v-if="open && testRun"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div class="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-2xl bg-white p-6 shadow-xl">
      <div class="flex items-start justify-between">
        <h2 class="text-xl font-bold text-gray-900">Manage Test Cases</h2>
        <button type="button" class="text-gray-500 hover:text-gray-700" :disabled="isSaving" @click="handleClose">
          <XIcon :size="18" />
        </button>
      </div>

      <div class="mt-4 flex-1 overflow-y-auto">
        <TestCaseSelector :project-id="projectId" v-model="selectedTestCaseIds" list-height-class="h-[21rem]" />

        <!-- Konfirmasi: ada test case yang mau dilepas & sudah punya hasil/evidence -->
        <div v-if="isConfirmingRemoval" class="mt-4 rounded-xl bg-red-50 px-4 py-3">
          <p class="text-sm font-semibold text-red-700">
            {{ removedWithData.length }} test case yang akan dilepas sudah punya hasil eksekusi/evidence.
          </p>
          <p class="mt-1 text-xs text-red-500">
            Hasil eksekusi &amp; evidence-nya di run ini akan terhapus permanen dan tidak bisa dipulihkan.
          </p>
          <ul class="mt-2 list-inside list-disc text-xs text-red-600">
            <li v-for="r in removedWithData.slice(0, 5)" :key="r.id">{{ r.testCaseTitle }} ({{ r.status }})</li>
            <li v-if="removedWithData.length > 5">dan {{ removedWithData.length - 5 }} lainnya</li>
          </ul>
        </div>

        <p v-if="generalError" class="mt-3 text-sm text-red-600">{{ generalError }}</p>
      </div>

      <div class="mt-5 flex justify-end gap-3">
        <button
          type="button"
          :disabled="isSaving"
          class="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          @click="handleClose"
        >
          Cancel
        </button>
        <button
          type="button"
          :disabled="isSaving"
          class="rounded-xl px-5 py-2.5 text-sm font-semibold text-white transition-colors disabled:cursor-not-allowed disabled:opacity-60"
          :class="isConfirmingRemoval ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-900 hover:bg-gray-800'"
          @click="handleSaveClick"
        >
          {{ isSaving ? 'Saving...' : isConfirmingRemoval ? 'Yes, Save & Delete' : 'Save Changes' }}
        </button>
      </div>
    </div>
  </div>
</template>

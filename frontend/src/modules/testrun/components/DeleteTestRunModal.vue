<!-- frontend/src/modules/testrun/components/DeleteTestRunModal.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue';
import axios from 'axios';
import { testRunService } from '../services/testRun.service';
import type { TestRunResponse } from '../types/testRun.types';
import XIcon from '@/shared/components/icons/XIcon.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';

// Delete test run HANYA boleh OWNER project (disamakan dgn aturan create,
// requirement #6) -- tombol yang buka modal ini SUDAH disembunyikan dari
// COLLABORATOR di TestRunCard.vue, backend (TestRunController) tetap jadi
// penjaga akhir (403). Beda dgn delete test case (yang soft-delete), delete
// test run di sini PERMANEN (hard delete, lihat TestRunServiceImpl) --
// makanya copy-nya lebih tegas soal ini tidak bisa dibatalkan.
const props = defineProps<{
  open: boolean;
  projectId: string;
  testRun: TestRunResponse | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'deleted', testRunId: string): void;
}>();

const isSubmitting = ref(false);
const generalError = ref('');

watch(
  () => [props.open, props.testRun?.id] as const,
  ([isOpen]) => {
    if (isOpen) generalError.value = '';
  }
);

function handleClose() {
  if (isSubmitting.value) return;
  emit('close');
}

async function handleConfirm() {
  if (!props.testRun) return;

  generalError.value = '';
  isSubmitting.value = true;
  try {
    await testRunService.deleteTestRun(props.projectId, props.testRun.id);
    emit('deleted', props.testRun.id);
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string };
      if (err.response?.status === 403) {
        generalError.value = data?.message ?? 'Hanya OWNER project yang bisa menghapus test run ini.';
      } else if (err.response?.status === 404) {
        generalError.value = data?.message ?? 'Test run tidak ditemukan (mungkin sudah dihapus).';
      } else {
        generalError.value = data?.message ?? 'Gagal menghapus test run, coba lagi';
      }
    } else {
      generalError.value = 'Tidak dapat terhubung ke server';
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <div
    v-if="open && testRun"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div class="w-full max-w-md rounded-2xl bg-white shadow-xl">
      <div class="flex items-start justify-between border-b border-gray-100 px-6 py-5">
        <div class="flex items-center gap-3">
          <span class="flex h-10 w-10 items-center justify-center rounded-full bg-red-50 text-red-600">
            <AlertCircleIcon :size="20" />
          </span>
          <h2 class="text-lg font-bold text-gray-900">Delete Test Run</h2>
        </div>
        <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
          <XIcon :size="20" />
        </button>
      </div>

      <div class="space-y-3 px-6 py-5">
        <p class="text-sm text-gray-600">
          Test run <span class="font-semibold text-gray-900">"{{ testRun.title }}"</span> beserta seluruh hasil
          eksekusi & evidence di dalamnya akan dihapus.
        </p>
        <p class="text-sm text-red-500">Tindakan ini permanen dan tidak bisa dibatalkan.</p>
        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
      </div>

      <div class="flex justify-end gap-3 border-t border-gray-100 px-6 py-4">
        <button
          type="button"
          class="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          :disabled="isSubmitting"
          @click="handleClose"
        >
          Cancel
        </button>
        <button
          type="button"
          :disabled="isSubmitting"
          class="rounded-xl bg-red-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
          @click="handleConfirm"
        >
          {{ isSubmitting ? 'Deleting...' : 'Delete Test Run' }}
        </button>
      </div>
    </div>
  </div>
</template>

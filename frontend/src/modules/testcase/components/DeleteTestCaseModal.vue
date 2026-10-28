<!-- frontend/src/modules/testcase/components/DeleteTestCaseModal.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue';
import axios from 'axios';
import { testCaseService } from '../services/testCase.service';
import type { TestCaseResponse } from '../types/testCase.types';
import XIcon from '@/shared/components/icons/XIcon.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';

// Requirement tambahan #1 & #2: delete test case = SOFT DELETE (backend
// men-flag status jadi ARCHIVED, bukan hapus permanen dari database) --
// makanya copy di modal ini SENGAJA bilang "diarsipkan", bukan "dihapus
// permanen", supaya user tidak salah kira datanya hilang total.
// Requirement #3: tombol yang membuka modal ini HANYA dirender kalau
// isOwner true (lihat TestCasePanel.vue) -- tapi backend TETAP jadi
// penjaga akhir (403 kalau somehow yang request bukan OWNER).
const props = defineProps<{
  open: boolean;
  projectId: string;
  testCase: TestCaseResponse | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'deleted', testCaseId: string): void;
}>();

const isSubmitting = ref(false);
const generalError = ref('');

// Reset pesan error tiap kali modal dibuka utk test case yang beda.
watch(
  () => [props.open, props.testCase?.id] as const,
  ([isOpen]) => {
    if (isOpen) {
      generalError.value = '';
    }
  }
);

function handleClose() {
  if (isSubmitting.value) return;
  emit('close');
}

async function handleConfirm() {
  if (!props.testCase) return;

  generalError.value = '';
  isSubmitting.value = true;
  try {
    await testCaseService.archiveTestCase(props.projectId, props.testCase.id);
    emit('deleted', props.testCase.id);
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string };
      if (err.response?.status === 403) {
        // Requirement #3 -- penjaga akhir di backend, harusnya jarang
        // kejadian di FE karena tombol delete sudah disembunyikan dari
        // COLLABORATOR (lihat TestCasePanel.vue).
        generalError.value = data?.message ?? 'Hanya OWNER project yang bisa menghapus test case ini.';
      } else if (err.response?.status === 409) {
        generalError.value = data?.message ?? 'Test case ini sudah diarsipkan sebelumnya.';
      } else if (err.response?.status === 404) {
        generalError.value = data?.message ?? 'Test case tidak ditemukan (mungkin sudah dihapus).';
      } else {
        generalError.value = data?.message ?? 'Gagal menghapus test case, coba lagi';
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
    v-if="open && testCase"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div class="w-full max-w-md rounded-2xl bg-white shadow-xl">
      <div class="flex items-start justify-between border-b border-gray-100 px-6 py-5">
        <div class="flex items-center gap-3">
          <span class="flex h-10 w-10 items-center justify-center rounded-full bg-red-50 text-red-600">
            <AlertCircleIcon :size="20" />
          </span>
          <h2 class="text-lg font-bold text-gray-900">Delete Test Case</h2>
        </div>
        <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
          <XIcon :size="20" />
        </button>
      </div>

      <div class="space-y-3 px-6 py-5">
        <p class="text-sm text-gray-600">
          Test case <span class="font-semibold text-gray-900">"{{ testCase.title }}"</span> akan diarsipkan.
        </p>
        <p class="text-sm text-gray-400">
          Test case yang sudah diarsipkan tidak akan muncul lagi di daftar test case, dan hanya bisa dilihat kembali
          oleh <span class="font-medium text-gray-600">Project Owner</span>.
        </p>
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
          {{ isSubmitting ? 'Deleting...' : 'Delete Test Case' }}
        </button>
      </div>
    </div>
  </div>
</template>

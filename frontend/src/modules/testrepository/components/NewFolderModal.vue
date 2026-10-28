<!-- frontend/src/modules/testrepository/components/NewFolderModal.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import axios from 'axios';
import { testFolderService } from '../services/testFolder.service';
import { flattenTreeForDropdown, buildFolderTree } from '../utils/testFolderTree';
import type { TestFolderResponse } from '../types/testFolder.types';
import XIcon from '@/shared/components/icons/XIcon.vue';

const props = defineProps<{
  open: boolean;
  projectId: string;
  // Flat list folder yang SUDAH ada di project ini -- dipakai isi dropdown
  // "Parent" (di-flatten+indent di sini, bukan minta parent component susun).
  folders: TestFolderResponse[];
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'created', folder: TestFolderResponse): void;
}>();

const folderName = ref('');
const parentId = ref<string>(''); // '' = Root (requirement #2)
const isSubmitting = ref(false);
const generalError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const isFormValid = computed(() => folderName.value.trim().length > 0);

const parentOptions = computed(() => flattenTreeForDropdown(buildFolderTree(props.folders)));

function resetForm() {
  folderName.value = '';
  parentId.value = '';
  generalError.value = '';
  fieldErrors.value = {};
}

function handleClose() {
  if (isSubmitting.value) return;
  resetForm();
  emit('close');
}

async function handleSubmit() {
  generalError.value = '';
  fieldErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    const created = await testFolderService.createFolder(props.projectId, {
      folderName: folderName.value.trim(),
      parentId: parentId.value || null,
    });
    emit('created', created);
    resetForm();
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        fieldErrors.value = data.errors;
      } else if (err.response?.status === 403) {
        // Requirement #3 -- backend balas 403 kalau bukan OWNER project.
        generalError.value = data?.message ?? 'Hanya owner project yang bisa membuat test suite/folder.';
      } else {
        generalError.value = data?.message ?? 'Gagal membuat folder, coba lagi';
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
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    @click.self="handleClose"
  >
    <div class="w-full max-w-md rounded-2xl bg-white p-8 shadow-xl">
      <div class="mb-6 flex items-start justify-between">
        <h2 class="text-xl font-bold text-gray-900">New Folder</h2>
        <button
          type="button"
          class="text-gray-400 hover:text-gray-600"
          :disabled="isSubmitting"
          @click="handleClose"
        >
          <XIcon :size="20" />
        </button>
      </div>

      <form class="space-y-5" @submit.prevent="handleSubmit">
        <div>
          <label for="folder-name" class="mb-2 block text-sm font-semibold text-gray-900"> Name </label>
          <input
            id="folder-name"
            v-model="folderName"
            type="text"
            placeholder="Folder name"
            class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            :class="{ 'border-red-500': fieldErrors.folderName }"
          />
          <p v-if="fieldErrors.folderName" class="mt-1.5 text-sm text-red-600">
            {{ fieldErrors.folderName }}
          </p>
        </div>

        <div>
          <label for="folder-parent" class="mb-2 block text-sm font-semibold text-gray-900"> Parent </label>
          <select
            id="folder-parent"
            v-model="parentId"
            class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          >
            <option value="">Root</option>
            <option v-for="option in parentOptions" :key="option.id" :value="option.id">
              {{ option.label }}
            </option>
          </select>
        </div>

        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

        <button
          type="submit"
          :disabled="!isFormValid || isSubmitting"
          class="w-full rounded-xl bg-gray-900 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Creating...' : 'Create' }}
        </button>
      </form>
    </div>
  </div>
</template>

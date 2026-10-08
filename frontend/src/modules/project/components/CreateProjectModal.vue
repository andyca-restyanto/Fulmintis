<!-- frontend/src/modules/project/components/CreateProjectModal.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import axios from 'axios';
import { projectService } from '../services/project.service';
import type { ProjectResponse } from '../types/project.types';
import XIcon from '@/shared/components/icons/XIcon.vue';

defineProps<{ open: boolean }>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'created', project: ProjectResponse): void;
}>();

const projectName = ref('');
const description = ref('');
const isSubmitting = ref(false);
const generalError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const isFormValid = computed(() => projectName.value.trim().length > 0);

function resetForm() {
  projectName.value = '';
  description.value = '';
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
    const created = await projectService.createProject({
      projectName: projectName.value.trim(),
      description: description.value.trim() || undefined,
    });
    emit('created', created);
    resetForm();
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        fieldErrors.value = data.errors;
      } else {
        generalError.value = data?.message ?? 'Gagal membuat project, coba lagi';
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
        <div>
          <h2 class="text-xl font-bold text-gray-900">Create New Project</h2>
          <p class="mt-1 text-sm text-gray-500">Isi detail project untuk mulai mengelola test case.</p>
        </div>
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
          <label for="project-name" class="mb-2 block text-sm font-semibold text-gray-900">
            Project Name
          </label>
          <input
            id="project-name"
            v-model="projectName"
            type="text"
            placeholder="e.g. Website Redesign"
            class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            :class="{ 'border-red-500': fieldErrors.projectName }"
          />
          <p v-if="fieldErrors.projectName" class="mt-1.5 text-sm text-red-600">
            {{ fieldErrors.projectName }}
          </p>
        </div>

        <div>
          <label for="project-description" class="mb-2 block text-sm font-semibold text-gray-900">
            Description <span class="font-normal text-gray-400">(optional)</span>
          </label>
          <textarea
            id="project-description"
            v-model="description"
            rows="3"
            placeholder="Tell us a bit about this project..."
            class="w-full resize-none rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

        <div class="flex gap-3 pt-2">
          <button
            type="button"
            class="flex-1 rounded-xl border border-gray-200 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50"
            :disabled="isSubmitting"
            @click="handleClose"
          >
            Cancel
          </button>
          <button
            type="submit"
            :disabled="!isFormValid || isSubmitting"
            class="flex-1 rounded-xl bg-gray-900 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
          >
            {{ isSubmitting ? 'Creating...' : 'Create Project' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

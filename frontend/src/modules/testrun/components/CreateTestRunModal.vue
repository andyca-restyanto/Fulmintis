<!-- frontend/src/modules/testrun/components/CreateTestRunModal.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import axios from 'axios';
import { testRunService } from '../services/testRun.service';
import type { TestRunDetailResponse } from '../types/testRun.types';
import TestCaseSelector from './TestCaseSelector.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';
import ArrowRightIcon from '@/shared/components/icons/ArrowRightIcon.vue';
import ArrowLeftIcon from '@/shared/components/icons/ArrowLeftIcon.vue';

// Requirement tambahan #1, #4, #6: create test run HANYA boleh dipanggil
// dari tombol yang sudah owner-only di TestRunListView.vue -- backend
// (TestRunController) tetap jadi penjaga akhir (403 kalau somehow bukan
// owner). Flow: step "Details" (title+description) -> step "Select Cases"
// -> 1x submit "Create Run" yang ngirim SEMUANYA sekaligus (title,
// description, testCaseIds) ke POST /test-runs, persis flow yang diminta.
const props = defineProps<{
  open: boolean;
  projectId: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'created', testRun: TestRunDetailResponse): void;
}>();

type Step = 'details' | 'select-cases';
const step = ref<Step>('details');

const title = ref('');
const description = ref('');
const selectedTestCaseIds = ref<string[]>([]);

const isSubmitting = ref(false);
const generalError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const isDetailsValid = computed(() => title.value.trim().length > 0);

// Form di-reset tiap kali modal DIBUKA (bukan tiap props berubah selagi
// terbuka) -- supaya "New Test Run" berikutnya selalu mulai dari step
// "Details" & form kosong, walau sebelumnya sempat diisi lalu dibatalkan.
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      step.value = 'details';
      title.value = '';
      description.value = '';
      selectedTestCaseIds.value = [];
      generalError.value = '';
      fieldErrors.value = {};
    }
  }
);

function handleClose() {
  if (isSubmitting.value) return;
  emit('close');
}

function goToSelectCases() {
  if (!isDetailsValid.value) return;
  step.value = 'select-cases';
}

function goBackToDetails() {
  step.value = 'details';
}

async function handleCreate() {
  if (selectedTestCaseIds.value.length === 0) return;

  generalError.value = '';
  fieldErrors.value = {};
  isSubmitting.value = true;
  try {
    const created = await testRunService.createTestRun(props.projectId, {
      title: title.value.trim(),
      description: description.value.trim() || null,
      testCaseIds: selectedTestCaseIds.value,
    });
    emit('created', created);
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        // Error validasi (mis. title kosong) -- balik ke step Details
        // supaya user langsung lihat field-nya.
        fieldErrors.value = data.errors;
        step.value = 'details';
      } else if (err.response?.status === 403) {
        generalError.value = data?.message ?? 'Hanya OWNER project yang bisa membuat test run.';
      } else if (err.response?.status === 404) {
        generalError.value = data?.message ?? 'Ada test case yang tidak valid, coba pilih ulang.';
      } else {
        generalError.value = data?.message ?? 'Gagal membuat test run, coba lagi';
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
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="handleClose"
  >
    <div class="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white shadow-xl">
      <!-- Step 1: Details -->
      <template v-if="step === 'details'">
        <div class="flex items-start justify-between border-b border-gray-100 px-8 py-6">
          <h2 class="text-xl font-bold text-gray-900">Create Test Run &mdash; Details</h2>
          <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
            <XIcon :size="20" />
          </button>
        </div>

        <div class="flex-1 space-y-5 overflow-y-auto px-8 py-6">
          <div>
            <label for="ctr-title" class="mb-2 block text-sm font-semibold text-gray-900">Title</label>
            <input
              id="ctr-title"
              v-model="title"
              type="text"
              placeholder="e.g. Sprint 1"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': fieldErrors.title }"
              @keyup.enter="goToSelectCases"
            />
            <p v-if="fieldErrors.title" class="mt-1.5 text-sm text-red-600">{{ fieldErrors.title }}</p>
          </div>

          <div>
            <label for="ctr-description" class="mb-2 block text-sm font-semibold text-gray-900">
              Description (optional)
            </label>
            <textarea
              id="ctr-description"
              v-model="description"
              rows="4"
              placeholder="What's this test run for?"
              class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            />
          </div>

          <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
        </div>

        <div class="flex justify-end border-t border-gray-100 px-8 py-5">
          <button
            type="button"
            :disabled="!isDetailsValid"
            class="flex items-center gap-2 rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
            @click="goToSelectCases"
          >
            Next: Select Cases
            <ArrowRightIcon :size="16" />
          </button>
        </div>
      </template>

      <!-- Step 2: Select Cases -->
      <template v-else>
        <div class="flex items-start justify-between border-b border-gray-100 px-8 py-6">
          <h2 class="text-xl font-bold text-gray-900">Create Test Run &mdash; Select Cases</h2>
          <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
            <XIcon :size="20" />
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-8 py-6">
          <TestCaseSelector :project-id="projectId" v-model="selectedTestCaseIds" />
          <p v-if="generalError" class="mt-3 text-sm text-red-600">{{ generalError }}</p>
        </div>

        <div class="flex items-center justify-between border-t border-gray-100 px-8 py-5">
          <button
            type="button"
            :disabled="isSubmitting"
            class="flex items-center gap-2 rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
            @click="goBackToDetails"
          >
            <ArrowLeftIcon :size="16" />
            Back
          </button>
          <button
            type="button"
            :disabled="selectedTestCaseIds.length === 0 || isSubmitting"
            class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
            @click="handleCreate"
          >
            {{ isSubmitting ? 'Creating...' : `Create Run (${selectedTestCaseIds.length} cases)` }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

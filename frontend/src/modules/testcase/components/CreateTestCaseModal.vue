<!-- frontend/src/modules/testcase/components/CreateTestCaseModal.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import axios from 'axios';
import { testCaseService } from '../services/testCase.service';
import { PRIORITY_OPTIONS, TYPE_OPTIONS } from '../utils/testCaseDisplay';
import { createEmptyStepRow, serializeSteps, type TestStepRow } from '../utils/testStepSerializer';
import type {
  CreateTestCaseRequest,
  TestCasePriority,
  TestCaseScenarioType,
  TestCaseType,
  TestCaseResponse,
} from '../types/testCase.types';
import XIcon from '@/shared/components/icons/XIcon.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';

const props = defineProps<{
  open: boolean;
  projectId: string;
  folderId: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'created', testCase: TestCaseResponse): void;
}>();

const title = ref('');
const priority = ref<TestCasePriority>('MEDIUM');
const type = ref<TestCaseType>('MANUAL');
const scenarioType = ref<TestCaseScenarioType | null>(null);
const description = ref('');
const objective = ref('');
const precondition = ref('');

// Test Steps & Expected Results -- SENGAJA dibuat tabel step terpisah
// (BUKAN 1 textarea polos spt Pre-conditions), sesuai mockup. Masing-
// masing baris punya id lokal FE-only (bukan dari backend) cuma buat :key,
// supaya list tetap stabil pas ada baris dihapus di tengah.
let stepIdCounter = 0;
function makeStepRow(): TestStepRow & { id: number } {
  return { id: ++stepIdCounter, ...createEmptyStepRow() };
}
const steps = ref<(TestStepRow & { id: number })[]>([makeStepRow()]);

const isSubmitting = ref(false);
const generalError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const isFormValid = computed(() => title.value.trim().length > 0);

function addStep() {
  steps.value.push(makeStepRow());
}

function removeStep(id: number) {
  // Minimal 1 baris tetap ada di form (spt mockup -- baris #1 selalu ada).
  if (steps.value.length <= 1) return;
  steps.value = steps.value.filter((row) => row.id !== id);
}

function resetForm() {
  title.value = '';
  priority.value = 'MEDIUM';
  type.value = 'MANUAL';
  scenarioType.value = null;
  description.value = '';
  objective.value = '';
  precondition.value = '';
  steps.value = [makeStepRow()];
  generalError.value = '';
  fieldErrors.value = {};
}

function handleClose() {
  if (isSubmitting.value) return;
  resetForm();
  emit('close');
}

// Klik ulang tombol yang sudah aktif -> deselect (scenarioType opsional
// di backend, jadi boleh tidak dipilih sama sekali).
function selectScenarioType(value: TestCaseScenarioType) {
  scenarioType.value = scenarioType.value === value ? null : value;
}

async function handleSubmit() {
  generalError.value = '';
  fieldErrors.value = {};
  if (!isFormValid.value) return;

  // Tabel step di-"gepengkan" jadi 2 teks bernomor di sini -- backend cuma
  // punya kolom test_step & expected_result yang flat TEXT (lihat komentar
  // di testStepSerializer.ts).
  const { testStep, expectedResult } = serializeSteps(steps.value);

  const payload: CreateTestCaseRequest = {
    folderId: props.folderId,
    title: title.value.trim(),
    priority: priority.value,
    type: type.value,
    scenarioType: scenarioType.value,
    description: description.value.trim() || null,
    objective: objective.value.trim() || null,
    precondition: precondition.value.trim() || null,
    testStep,
    expectedResult,
  };

  isSubmitting.value = true;
  try {
    const created = await testCaseService.createTestCase(props.projectId, payload);
    emit('created', created);
    resetForm();
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        fieldErrors.value = data.errors;
      } else {
        generalError.value = data?.message ?? 'Gagal membuat test case, coba lagi';
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
    <div class="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-2xl bg-white shadow-xl">
      <div class="flex items-start justify-between border-b border-gray-100 px-8 py-6">
        <h2 class="text-xl font-bold text-gray-900">Create Test Case</h2>
        <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
          <XIcon :size="20" />
        </button>
      </div>

      <form id="create-test-case-form" class="flex-1 space-y-5 overflow-y-auto px-8 py-6" @submit.prevent="handleSubmit">
        <div>
          <label for="tc-title" class="mb-2 block text-sm font-semibold text-gray-900">Title</label>
          <input
            id="tc-title"
            v-model="title"
            type="text"
            placeholder="Enter test case title"
            class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            :class="{ 'border-red-500': fieldErrors.title }"
          />
          <p v-if="fieldErrors.title" class="mt-1.5 text-sm text-red-600">{{ fieldErrors.title }}</p>
        </div>

        <div class="grid grid-cols-1 gap-5 sm:grid-cols-3">
          <div>
            <label for="tc-priority" class="mb-2 block text-sm font-semibold text-gray-900">Priority</label>
            <select
              id="tc-priority"
              v-model="priority"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            >
              <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div>
            <label for="tc-type" class="mb-2 block text-sm font-semibold text-gray-900">Type</label>
            <select
              id="tc-type"
              v-model="type"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            >
              <option v-for="option in TYPE_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div>
            <span class="mb-2 block text-sm font-semibold text-gray-900">Scenario Type</span>
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 rounded-xl border px-3 py-3 text-sm font-medium transition-colors"
                :class="
                  scenarioType === 'POSITIVE'
                    ? 'border-emerald-600 bg-emerald-600 text-white'
                    : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                "
                @click="selectScenarioType('POSITIVE')"
              >
                ✅ Positive
              </button>
              <button
                type="button"
                class="flex-1 rounded-xl border px-3 py-3 text-sm font-medium transition-colors"
                :class="
                  scenarioType === 'NEGATIVE'
                    ? 'border-red-600 bg-red-600 text-white'
                    : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                "
                @click="selectScenarioType('NEGATIVE')"
              >
                ❌ Negative
              </button>
            </div>
          </div>
        </div>

        <div>
          <label for="tc-description" class="mb-2 block text-sm font-semibold text-gray-900">Description</label>
          <textarea
            id="tc-description"
            v-model="description"
            rows="3"
            placeholder="Brief summary of the test case"
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <div>
          <label for="tc-objective" class="mb-2 block text-sm font-semibold text-gray-900">Objective</label>
          <textarea
            id="tc-objective"
            v-model="objective"
            rows="3"
            placeholder="What is the goal of this test?"
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <div>
          <label for="tc-precondition" class="mb-2 block text-sm font-semibold text-gray-900">Pre-conditions</label>
          <textarea
            id="tc-precondition"
            v-model="precondition"
            rows="3"
            placeholder='e.g. "User is logged in"'
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <!-- Test Steps & Expected Results -- tabel step terpisah (BUKAN
             textarea polos spt Pre-conditions di atas), sesuai mockup &
             instruksi eksplisit. Digabung jadi teks bernomor sebelum
             dikirim ke API -- lihat serializeSteps() di
             utils/testStepSerializer.ts. -->
        <div>
          <div class="mb-2 flex items-center justify-between">
            <span class="block text-sm font-semibold text-gray-900">Test Steps &amp; Expected Results</span>
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
              @click="addStep"
            >
              <PlusIcon :size="14" />
              Add Step
            </button>
          </div>

          <div class="overflow-hidden rounded-xl border border-gray-200">
            <table class="w-full text-left text-sm">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 text-xs font-medium uppercase tracking-wide text-gray-400">
                  <th class="w-10 px-3 py-2.5 font-medium">#</th>
                  <th class="px-3 py-2.5 font-medium">Step Action</th>
                  <th class="px-3 py-2.5 font-medium">Expected Result</th>
                  <th class="w-10 px-3 py-2.5" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in steps" :key="row.id" class="border-b border-gray-100 last:border-0">
                  <td class="px-3 py-2 align-top text-gray-400">{{ index + 1 }}</td>
                  <td class="px-2 py-2 align-top">
                    <input
                      v-model="row.action"
                      type="text"
                      placeholder="What the user does"
                      class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
                    />
                  </td>
                  <td class="px-2 py-2 align-top">
                    <input
                      v-model="row.expectedResult"
                      type="text"
                      placeholder="What should happen"
                      class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
                    />
                  </td>
                  <td class="px-2 py-2 text-center align-top">
                    <button
                      type="button"
                      class="p-1.5 text-gray-300 hover:text-red-500 disabled:cursor-not-allowed disabled:hover:text-gray-300"
                      :disabled="steps.length <= 1"
                      title="Hapus step ini"
                      @click="removeStep(row.id)"
                    >
                      <TrashIcon :size="16" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
      </form>

      <div class="flex justify-end gap-3 border-t border-gray-100 px-8 py-5">
        <button
          type="button"
          class="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          :disabled="isSubmitting"
          @click="handleClose"
        >
          Cancel
        </button>
        <button
          type="submit"
          form="create-test-case-form"
          :disabled="!isFormValid || isSubmitting"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Creating...' : 'Create Test Case' }}
        </button>
      </div>
    </div>
  </div>
</template>

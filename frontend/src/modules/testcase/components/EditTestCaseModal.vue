<!-- frontend/src/modules/testcase/components/EditTestCaseModal.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import axios from 'axios';
import { testCaseService } from '../services/testCase.service';
import { PRIORITY_OPTIONS, TYPE_OPTIONS } from '../utils/testCaseDisplay';
import { createEmptyStepRow, parseSteps, serializeSteps, type TestStepRow } from '../utils/testStepSerializer';
import type {
  TestCasePriority,
  TestCaseScenarioType,
  TestCaseType,
  TestCaseResponse,
  UpdateTestCaseRequest,
} from '../types/testCase.types';
import XIcon from '@/shared/components/icons/XIcon.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';

// Requirement tambahan #1: edit test case yang sudah dibuat. Form ini
// SENGAJA dibuat mirror 1:1 dgn CreateTestCaseModal.vue (field & tabel step
// sama persis) supaya konsisten dgn UX create, cuma beda: pre-filled dari
// data existing & submit-nya PUT (bukan POST).
const props = defineProps<{
  open: boolean;
  projectId: string;
  // Test case yang mau diedit -- SEMUA field form di-pre-fill dari sini.
  // Sengaja terima objek penuh (bukan cuma id lalu fetch ulang) karena
  // TestCasePanel sudah punya data lengkapnya di memori dari hasil search
  // (TestCaseResponseDTO sudah termasuk description/objective/precondition/
  // testStep/expectedResult) -- tidak perlu bolak-balik network.
  testCase: TestCaseResponse | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'updated', testCase: TestCaseResponse): void;
}>();

const title = ref('');
const priority = ref<TestCasePriority>('MEDIUM');
const type = ref<TestCaseType>('MANUAL');
const scenarioType = ref<TestCaseScenarioType | null>(null);
const description = ref('');
const objective = ref('');
const precondition = ref('');

let stepIdCounter = 0;
function makeStepRow(base?: TestStepRow): TestStepRow & { id: number } {
  return { id: ++stepIdCounter, ...(base ?? createEmptyStepRow()) };
}
const steps = ref<(TestStepRow & { id: number })[]>([makeStepRow()]);

// folderId TIDAK ditampilkan sbg field yang bisa diubah di form ini (UX:
// user edit test case dari dalam 1 folder yang sedang dibuka, bukan pindah
// folder) -- tapi backend (UpdateTestCaseRequestDTO) tetap mewajibkan
// folderId dikirim, jadi disimpan apa adanya dari testCase yang diedit.
const folderId = ref('');

const isSubmitting = ref(false);
const generalError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const isFormValid = computed(() => title.value.trim().length > 0);

function addStep() {
  steps.value.push(makeStepRow());
}

function removeStep(id: number) {
  if (steps.value.length <= 1) return;
  steps.value = steps.value.filter((row) => row.id !== id);
}

// Setiap kali modal dibuka utk test case yang beda (atau dibuka ulang),
// form di-populate ulang dari props.testCase -- bukan cuma sekali saat
// mount, karena instance modal ini dipakai ulang utk test case manapun yang
// diklik "Edit" (lihat TestCasePanel.vue).
function populateForm(testCase: TestCaseResponse) {
  title.value = testCase.title;
  priority.value = testCase.priority;
  type.value = testCase.type;
  scenarioType.value = testCase.scenarioType;
  description.value = testCase.description ?? '';
  objective.value = testCase.objective ?? '';
  precondition.value = testCase.precondition ?? '';
  folderId.value = testCase.folderId;
  steps.value = parseSteps(testCase.testStep, testCase.expectedResult).map((row) => makeStepRow(row));
  generalError.value = '';
  fieldErrors.value = {};
}

watch(
  () => [props.open, props.testCase] as const,
  ([isOpen, testCase]) => {
    if (isOpen && testCase) {
      populateForm(testCase);
    }
  },
  { immediate: true }
);

function handleClose() {
  if (isSubmitting.value) return;
  emit('close');
}

function selectScenarioType(value: TestCaseScenarioType) {
  scenarioType.value = scenarioType.value === value ? null : value;
}

async function handleSubmit() {
  if (!props.testCase) return;

  generalError.value = '';
  fieldErrors.value = {};
  if (!isFormValid.value) return;

  const { testStep, expectedResult } = serializeSteps(steps.value);

  const payload: UpdateTestCaseRequest = {
    folderId: folderId.value,
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
    const updated = await testCaseService.updateTestCase(props.projectId, props.testCase.id, payload);
    emit('updated', updated);
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        fieldErrors.value = data.errors;
      } else if (err.response?.status === 404) {
        // Requirement #4: bisa kejadian kalau test case ini di-archive OWNER
        // lain sesaat sebelum submit (COLLABORATOR) -- backend balas 404.
        generalError.value = data?.message ?? 'Test case tidak ditemukan (mungkin sudah dihapus).';
      } else {
        generalError.value = data?.message ?? 'Gagal menyimpan perubahan, coba lagi';
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
        <h2 class="text-xl font-bold text-gray-900">Edit Test Case</h2>
        <button type="button" class="text-gray-400 hover:text-gray-600" :disabled="isSubmitting" @click="handleClose">
          <XIcon :size="20" />
        </button>
      </div>

      <form id="edit-test-case-form" class="flex-1 space-y-5 overflow-y-auto px-8 py-6" @submit.prevent="handleSubmit">
        <div>
          <label for="etc-title" class="mb-2 block text-sm font-semibold text-gray-900">Title</label>
          <input
            id="etc-title"
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
            <label for="etc-priority" class="mb-2 block text-sm font-semibold text-gray-900">Priority</label>
            <select
              id="etc-priority"
              v-model="priority"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-900 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            >
              <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div>
            <label for="etc-type" class="mb-2 block text-sm font-semibold text-gray-900">Type</label>
            <select
              id="etc-type"
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
          <label for="etc-description" class="mb-2 block text-sm font-semibold text-gray-900">Description</label>
          <textarea
            id="etc-description"
            v-model="description"
            rows="3"
            placeholder="Brief summary of the test case"
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <div>
          <label for="etc-objective" class="mb-2 block text-sm font-semibold text-gray-900">Objective</label>
          <textarea
            id="etc-objective"
            v-model="objective"
            rows="3"
            placeholder="What is the goal of this test?"
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

        <div>
          <label for="etc-precondition" class="mb-2 block text-sm font-semibold text-gray-900">Pre-conditions</label>
          <textarea
            id="etc-precondition"
            v-model="precondition"
            rows="3"
            placeholder='e.g. "User is logged in"'
            class="w-full resize-y rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          />
        </div>

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
          form="edit-test-case-form"
          :disabled="!isFormValid || isSubmitting"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Saving...' : 'Save Changes' }}
        </button>
      </div>
    </div>
  </div>
</template>

<!-- frontend/src/modules/testcase/components/AiDraftCard.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { PRIORITY_OPTIONS, TYPE_OPTIONS, priorityBadgeClass, priorityLabel, scenarioBadgeClass, scenarioLabel, typeLabel } from '../utils/testCaseDisplay';
import { TITLE_MAX, displayErrors, newStep, type DraftFieldKey, type EditableDraft } from '../utils/testCaseAiDraft';
import type { TestCaseScenarioType } from '../types/testCase.types';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import ChevronRightIcon from '@/shared/components/icons/ChevronRightIcon.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import TrashIcon from '@/shared/components/icons/TrashIcon.vue';

// Satu draft hasil AI di layar review. Draft adalah objek reaktif milik daftar di modal; kartu ini mengeditnya LANGSUNG (v-model
// pada model), jadi tidak ada salinan yang bisa tidak sinkron. Diciutkan secara bawaan supaya 15 draft tetap enak dibaca.
const draft = defineModel<EditableDraft>({ required: true });

const props = defineProps<{
  /** true = sedang menyimpan: semua kolom dikunci. */
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'remove', tempId: string): void;
}>();

const errors = computed(() => displayErrors(draft.value));
const hasErrors = computed(() => Object.keys(errors.value).length > 0);
const filledSteps = computed(() => draft.value.steps.filter((row) => row.action.trim() !== '' || row.expectedResult.trim() !== '').length);
const idPrefix = computed(() => `ai-draft-${draft.value.tempId}`);

// Mengubah sebuah kolom menghapus pesan error server utk kolom itu (pesan itu soal isi lama, bukan isi yang baru diketik).
function clearServerError(key: DraftFieldKey) {
  delete draft.value.serverErrors[key];
}

function toggleExpanded() {
  draft.value.expanded = !draft.value.expanded;
}

// Klik ulang tombol yang sudah aktif -> deselect (scenarioType opsional), sama dgn form create test case.
function selectScenario(value: TestCaseScenarioType) {
  draft.value.scenarioType = draft.value.scenarioType === value ? null : value;
}

function addStep() {
  draft.value.steps.push(newStep());
  clearServerError('steps');
}

function removeStep(id: number) {
  // Minimal 1 baris tetap ada (baris #1 selalu ada), sama dgn form create.
  if (draft.value.steps.length <= 1) return;
  draft.value.steps = draft.value.steps.filter((row) => row.id !== id);
  clearServerError('steps');
}

const inputClass =
  'w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100 disabled:bg-gray-50 disabled:text-gray-400';
</script>

<template>
  <article
    class="rounded-2xl border bg-white"
    :class="[hasErrors ? 'border-red-300' : 'border-gray-200', draft.selected ? '' : 'opacity-60']"
  >
    <!-- Ringkasan (selalu tampil) -->
    <div class="flex items-start gap-3 px-4 py-3">
      <input
        :id="`${idPrefix}-select`"
        v-model="draft.selected"
        type="checkbox"
        class="mt-1 h-4 w-4 shrink-0 rounded border-gray-300 text-gray-900 focus:ring-gray-300"
        :disabled="props.disabled"
        :aria-label="`Pilih draft ${draft.title || 'tanpa judul'}`"
      />

      <button
        type="button"
        class="flex min-w-0 flex-1 items-start gap-2 text-left"
        :aria-expanded="draft.expanded"
        :aria-controls="`${idPrefix}-body`"
        @click="toggleExpanded"
      >
        <component :is="draft.expanded ? ChevronDownIcon : ChevronRightIcon" :size="16" class="mt-0.5 shrink-0 text-gray-400" />
        <span class="min-w-0 flex-1">
          <span class="block truncate text-sm font-semibold text-gray-900" :title="draft.title">
            {{ draft.title.trim() || '(tanpa judul)' }}
          </span>
          <span class="mt-1 flex flex-wrap items-center gap-1.5 text-xs">
            <span class="rounded-full px-2 py-0.5 font-medium" :class="priorityBadgeClass(draft.priority)">{{ priorityLabel(draft.priority) }}</span>
            <span class="rounded-full bg-gray-100 px-2 py-0.5 font-medium text-gray-600">{{ typeLabel(draft.type) }}</span>
            <span
              v-if="draft.scenarioType"
              class="rounded-full px-2 py-0.5 font-medium"
              :class="scenarioBadgeClass(draft.scenarioType)"
            >
              {{ scenarioLabel(draft.scenarioType) }}
            </span>
            <span class="text-gray-400">{{ filledSteps }} langkah</span>
            <span v-if="draft.duplicateOfExisting" class="rounded-full bg-amber-50 px-2 py-0.5 font-medium text-amber-700">
              Judul sama dengan test case di folder ini
            </span>
            <span v-if="hasErrors" class="rounded-full bg-red-50 px-2 py-0.5 font-medium text-red-700">Perlu diperbaiki</span>
          </span>
        </span>
      </button>

      <button
        type="button"
        class="shrink-0 rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="props.disabled"
        aria-label="Buang draft ini"
        title="Buang draft ini"
        @click="emit('remove', draft.tempId)"
      >
        <TrashIcon :size="16" />
      </button>
    </div>

    <!-- Form edit (dibuka) -->
    <div v-if="draft.expanded" :id="`${idPrefix}-body`" class="space-y-4 border-t border-gray-100 px-4 py-4">
      <div>
        <label :for="`${idPrefix}-title`" class="mb-1.5 block text-sm font-semibold text-gray-900">Title</label>
        <input
          :id="`${idPrefix}-title`"
          v-model="draft.title"
          type="text"
          :class="[inputClass, errors.title ? 'border-red-500' : '']"
          :disabled="props.disabled"
          @input="clearServerError('title')"
        />
        <div class="mt-1 flex items-start justify-between gap-3">
          <p v-if="errors.title" class="text-sm text-red-600" role="alert">{{ errors.title }}</p>
          <span v-else />
          <span class="shrink-0 text-xs tabular-nums" :class="draft.title.trim().length > TITLE_MAX ? 'text-red-600' : 'text-gray-400'">
            {{ draft.title.trim().length }} / {{ TITLE_MAX }}
          </span>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div>
          <label :for="`${idPrefix}-priority`" class="mb-1.5 block text-sm font-semibold text-gray-900">Priority</label>
          <select
            :id="`${idPrefix}-priority`"
            v-model="draft.priority"
            :class="[inputClass, errors.priority ? 'border-red-500' : '']"
            :disabled="props.disabled"
            @change="clearServerError('priority')"
          >
            <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <p v-if="errors.priority" class="mt-1 text-sm text-red-600" role="alert">{{ errors.priority }}</p>
        </div>

        <div>
          <label :for="`${idPrefix}-type`" class="mb-1.5 block text-sm font-semibold text-gray-900">Type</label>
          <select
            :id="`${idPrefix}-type`"
            v-model="draft.type"
            :class="[inputClass, errors.type ? 'border-red-500' : '']"
            :disabled="props.disabled"
            @change="clearServerError('type')"
          >
            <option v-for="option in TYPE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <p v-if="errors.type" class="mt-1 text-sm text-red-600" role="alert">{{ errors.type }}</p>
        </div>

        <div>
          <span class="mb-1.5 block text-sm font-semibold text-gray-900">Scenario Type</span>
          <div class="flex gap-2">
            <button
              type="button"
              class="flex-1 rounded-xl border px-3 py-2.5 text-sm font-medium transition-colors disabled:cursor-not-allowed"
              :class="
                draft.scenarioType === 'POSITIVE'
                  ? 'border-emerald-600 bg-emerald-600 text-white'
                  : 'border-gray-200 text-gray-600 hover:bg-gray-50'
              "
              :disabled="props.disabled"
              :aria-pressed="draft.scenarioType === 'POSITIVE'"
              @click="selectScenario('POSITIVE')"
            >
              ✅ Positive
            </button>
            <button
              type="button"
              class="flex-1 rounded-xl border px-3 py-2.5 text-sm font-medium transition-colors disabled:cursor-not-allowed"
              :class="
                draft.scenarioType === 'NEGATIVE'
                  ? 'border-red-600 bg-red-600 text-white'
                  : 'border-gray-200 text-gray-600 hover:bg-gray-50'
              "
              :disabled="props.disabled"
              :aria-pressed="draft.scenarioType === 'NEGATIVE'"
              @click="selectScenario('NEGATIVE')"
            >
              ❌ Negative
            </button>
          </div>
        </div>
      </div>

      <div>
        <label :for="`${idPrefix}-description`" class="mb-1.5 block text-sm font-semibold text-gray-900">Description</label>
        <textarea
          :id="`${idPrefix}-description`"
          v-model="draft.description"
          rows="2"
          :class="[inputClass, errors.description ? 'border-red-500' : '']"
          :disabled="props.disabled"
          @input="clearServerError('description')"
        />
        <p v-if="errors.description" class="mt-1 text-sm text-red-600" role="alert">{{ errors.description }}</p>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label :for="`${idPrefix}-objective`" class="mb-1.5 block text-sm font-semibold text-gray-900">Objective</label>
          <textarea
            :id="`${idPrefix}-objective`"
            v-model="draft.objective"
            rows="2"
            :class="[inputClass, errors.objective ? 'border-red-500' : '']"
            :disabled="props.disabled"
            @input="clearServerError('objective')"
          />
          <p v-if="errors.objective" class="mt-1 text-sm text-red-600" role="alert">{{ errors.objective }}</p>
        </div>
        <div>
          <label :for="`${idPrefix}-precondition`" class="mb-1.5 block text-sm font-semibold text-gray-900">Pre-conditions</label>
          <textarea
            :id="`${idPrefix}-precondition`"
            v-model="draft.precondition"
            rows="2"
            :class="[inputClass, errors.precondition ? 'border-red-500' : '']"
            :disabled="props.disabled"
            @input="clearServerError('precondition')"
          />
          <p v-if="errors.precondition" class="mt-1 text-sm text-red-600" role="alert">{{ errors.precondition }}</p>
        </div>
      </div>

      <!-- Langkah & hasil yang diharapkan (tabel, sama dgn form create) -->
      <div>
        <div class="mb-1.5 flex items-center justify-between">
          <span class="text-sm font-semibold text-gray-900">Test Steps &amp; Expected Results</span>
          <button
            type="button"
            class="flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="props.disabled"
            @click="addStep"
          >
            <PlusIcon :size="13" />
            Tambah langkah
          </button>
        </div>
        <div class="overflow-x-auto rounded-xl border" :class="errors.steps ? 'border-red-500' : 'border-gray-200'">
          <table class="w-full text-sm">
            <thead class="bg-gray-50 text-left text-xs font-semibold text-gray-500">
              <tr>
                <th class="w-10 px-3 py-2">#</th>
                <th class="px-3 py-2">Step</th>
                <th class="px-3 py-2">Expected Result</th>
                <th class="w-10 px-2 py-2"><span class="sr-only">Hapus</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in draft.steps" :key="row.id" class="border-t border-gray-100 align-top">
                <td class="px-3 py-2 text-gray-400">{{ index + 1 }}</td>
                <td class="px-2 py-1.5">
                  <input
                    v-model="row.action"
                    type="text"
                    class="w-full min-w-[10rem] rounded-lg border border-gray-200 px-2.5 py-1.5 text-sm focus:border-gray-400 focus:outline-none disabled:bg-gray-50"
                    :disabled="props.disabled"
                    :aria-label="`Langkah ${index + 1}`"
                    @input="clearServerError('steps')"
                  />
                </td>
                <td class="px-2 py-1.5">
                  <input
                    v-model="row.expectedResult"
                    type="text"
                    class="w-full min-w-[10rem] rounded-lg border border-gray-200 px-2.5 py-1.5 text-sm focus:border-gray-400 focus:outline-none disabled:bg-gray-50"
                    :disabled="props.disabled"
                    :aria-label="`Hasil yang diharapkan langkah ${index + 1}`"
                    @input="clearServerError('steps')"
                  />
                </td>
                <td class="px-2 py-1.5 text-center">
                  <button
                    type="button"
                    class="rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-30"
                    :disabled="props.disabled || draft.steps.length <= 1"
                    :aria-label="`Hapus langkah ${index + 1}`"
                    @click="removeStep(row.id)"
                  >
                    <TrashIcon :size="14" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="errors.steps" class="mt-1 text-sm text-red-600" role="alert">{{ errors.steps }}</p>
      </div>
    </div>
  </article>
</template>

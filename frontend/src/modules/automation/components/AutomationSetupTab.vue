<!-- frontend/src/modules/automation/components/AutomationSetupTab.vue -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import axios from 'axios';
import { automationService } from '../services/automation.service';
import type { AutomationFramework, AutomationLanguage, AutomationOptions, AutomationPattern, AutomationSetup } from '../types/automation.types';
import {
  STRUCTURE_NOTES_MAX,
  formFromSetup,
  frameworkLabel,
  isFormDirty,
  isFormValid,
  isLanguageSupported,
  languageAfterFrameworkChange,
  languageLabel,
  patternDescription,
  patternLabel,
  setupSummary,
  toSaveRequest,
  unsupportedReason,
  type SetupForm,
} from '../utils/automationOptions';
import { describeAutomationError } from '../utils/automationErrors';
import { formatWhen } from '../utils/automationFiles';
import { useToast } from '@/shared/composables/useToast';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

// Struktur/arsitektur automation project (maksimal 1 per project). Hanya OWNER yang bisa mengubah;
// anggota lain melihatnya read-only. Opsi dan matriks framework x bahasa datang dari backend.
const props = defineProps<{
  projectId: string;
  options: AutomationOptions;
  setup: AutomationSetup;
  isOwner: boolean;
}>();

const emit = defineEmits<{
  (e: 'saved', setup: AutomationSetup): void;
  (e: 'deleted'): void;
  (e: 'go-generate'): void;
}>();

const { pushToast } = useToast();

const form = ref<SetupForm>(formFromSetup(props.setup, props.options));
watch(
  () => props.setup,
  (setup) => {
    form.value = formFromSetup(setup, props.options);
  }
);

const isSaving = ref(false);
const isDeleting = ref(false);
const isConfirmingReset = ref(false);
const errorMessage = ref('');

const valid = computed(() => isFormValid(props.options, form.value));
const dirty = computed(() => isFormDirty(form.value, props.setup));
const canSave = computed(() => props.isOwner && valid.value && dirty.value && !isSaving.value);
const notesLength = computed(() => form.value.structureNotes.length);

function selectFramework(framework: AutomationFramework) {
  if (!props.isOwner) return;
  form.value.framework = framework;
  // Bahasa yang tidak valid utk framework baru otomatis diganti ke bahasa valid pertama.
  form.value.language = languageAfterFrameworkChange(props.options, framework, form.value.language);
}

function selectLanguage(language: AutomationLanguage) {
  if (!props.isOwner || !isLanguageSupported(props.options, form.value.framework, language)) return;
  form.value.language = language;
}

function selectPattern(pattern: AutomationPattern) {
  if (!props.isOwner) return;
  form.value.pattern = pattern;
}

function languageDisabledReason(language: AutomationLanguage): string | null {
  if (form.value.framework === null) return 'Pilih framework terlebih dahulu.';
  return isLanguageSupported(props.options, form.value.framework, language)
    ? null
    : unsupportedReason(form.value.framework, language);
}

async function save() {
  if (!canSave.value) return;
  isSaving.value = true;
  errorMessage.value = '';
  try {
    const saved = await automationService.saveSetup(props.projectId, toSaveRequest(form.value));
    emit('saved', saved);
    pushToast({ type: 'success', message: 'Setup automation disimpan.' });
  } catch (err) {
    errorMessage.value = axios.isAxiosError(err)
      ? describeAutomationError(err.response?.status, err.response?.data, 'Gagal menyimpan setup, coba lagi.').message
      : 'Tidak dapat terhubung ke server.';
  } finally {
    isSaving.value = false;
  }
}

async function resetSetup() {
  if (isDeleting.value) return;
  isDeleting.value = true;
  errorMessage.value = '';
  try {
    await automationService.deleteSetup(props.projectId);
    isConfirmingReset.value = false;
    emit('deleted');
    pushToast({ type: 'info', message: 'Setup automation dihapus. Riwayat generate tetap tersimpan.' });
  } catch (err) {
    isConfirmingReset.value = false;
    errorMessage.value = axios.isAxiosError(err)
      ? describeAutomationError(err.response?.status, err.response?.data, 'Gagal menghapus setup, coba lagi.').message
      : 'Tidak dapat terhubung ke server.';
  } finally {
    isDeleting.value = false;
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Info untuk non-OWNER -->
    <p
      v-if="!isOwner"
      class="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600"
      role="note"
    >
      Hanya <span class="font-semibold">Owner</span> project yang dapat mengubah setup automation.
      <template v-if="!setup.configured"> Setup belum diatur oleh Owner.</template>
    </p>

    <!-- Ringkasan setup tersimpan -->
    <div v-if="setup.configured" class="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-emerald-50/60 px-4 py-3">
      <p class="flex items-center gap-2 text-sm text-emerald-800">
        <CheckCircleIcon :size="16" class="shrink-0" />
        <span>
          Setup aktif: <span class="font-semibold">{{ setupSummary(setup) }}</span>
          <span class="ml-2 text-xs text-emerald-700/70">
            diubah {{ formatWhen(setup.updatedAt) }}<template v-if="setup.updatedBy"> oleh {{ setup.updatedBy }}</template>
          </span>
        </span>
      </p>
      <button
        type="button"
        class="rounded-lg border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold text-emerald-800 hover:bg-emerald-50"
        @click="emit('go-generate')"
      >
        Lanjut ke Generate
      </button>
    </div>

    <!-- Framework -->
    <section class="rounded-2xl border border-gray-200 p-6">
      <h3 class="text-base font-bold text-gray-900">Framework</h3>
      <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Framework">
        <button
          v-for="framework in options.frameworks"
          :key="framework"
          type="button"
          role="radio"
          :aria-checked="form.framework === framework"
          :disabled="!isOwner"
          class="rounded-xl border px-4 py-3 text-left text-sm font-semibold transition-colors disabled:cursor-not-allowed"
          :class="form.framework === framework ? 'border-gray-900 bg-gray-900 text-white' : 'border-gray-200 text-gray-800 hover:bg-gray-50 disabled:hover:bg-white'"
          @click="selectFramework(framework)"
        >
          {{ frameworkLabel(framework) }}
        </button>
      </div>
    </section>

    <!-- Bahasa -->
    <section class="rounded-2xl border border-gray-200 p-6">
      <h3 class="text-base font-bold text-gray-900">Bahasa pemrograman</h3>
      <div class="mt-4 flex flex-wrap gap-3" role="radiogroup" aria-label="Bahasa pemrograman">
        <button
          v-for="language in options.languages"
          :key="language"
          type="button"
          role="radio"
          :aria-checked="form.language === language"
          :aria-disabled="languageDisabledReason(language) !== null"
          :disabled="!isOwner || languageDisabledReason(language) !== null"
          :title="languageDisabledReason(language) ?? undefined"
          class="rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors disabled:cursor-not-allowed"
          :class="
            form.language === language
              ? 'border-gray-900 bg-gray-900 text-white'
              : languageDisabledReason(language) !== null
                ? 'border-dashed border-gray-200 text-gray-300 line-through'
                : 'border-gray-200 text-gray-800 hover:bg-gray-50 disabled:hover:bg-white'
          "
          @click="selectLanguage(language)"
        >
          {{ languageLabel(language) }}
        </button>
      </div>
      <p v-if="form.framework === null" class="mt-3 text-xs text-gray-400">Pilih framework dulu untuk melihat bahasa yang tersedia.</p>
      <p v-else class="mt-3 text-xs text-gray-400">
        Bahasa yang dicoret tidak didukung {{ frameworkLabel(form.framework) }}.
      </p>
    </section>

    <!-- Pola -->
    <section class="rounded-2xl border border-gray-200 p-6">
      <h3 class="text-base font-bold text-gray-900">Pola struktur</h3>
      <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Pola struktur">
        <button
          v-for="pattern in options.patterns"
          :key="pattern"
          type="button"
          role="radio"
          :aria-checked="form.pattern === pattern"
          :disabled="!isOwner"
          class="rounded-xl border px-4 py-3 text-left transition-colors disabled:cursor-not-allowed"
          :class="form.pattern === pattern ? 'border-gray-900 bg-gray-50' : 'border-gray-200 hover:bg-gray-50 disabled:hover:bg-white'"
          @click="selectPattern(pattern)"
        >
          <span class="block text-sm font-semibold text-gray-900">{{ patternLabel(pattern) }}</span>
          <span class="mt-1 block text-xs text-gray-500">{{ patternDescription(pattern) }}</span>
        </button>
      </div>
    </section>

    <!-- Struktur folder & aturan -->
    <section class="rounded-2xl border border-gray-200 p-6">
      <div class="flex items-baseline justify-between gap-3">
        <h3 class="text-base font-bold text-gray-900">Struktur folder &amp; aturan penamaan <span class="text-sm font-normal text-gray-400">(opsional)</span></h3>
        <span class="text-xs tabular-nums" :class="notesLength > STRUCTURE_NOTES_MAX ? 'text-red-600' : 'text-gray-400'">
          {{ notesLength }}/{{ STRUCTURE_NOTES_MAX }}
        </span>
      </div>
      <p class="mt-1 text-xs text-gray-500">AI akan mengikuti struktur ini saat menyusun berkas. Jangan menaruh password atau token asli di sini.</p>
      <textarea
        v-model="form.structureNotes"
        rows="6"
        :readonly="!isOwner"
        :aria-invalid="notesLength > STRUCTURE_NOTES_MAX"
        placeholder="Contoh:&#10;pages/        -> page object per halaman (LoginPage.ts)&#10;tests/        -> satu spec per fitur, nama: &lt;fitur&gt;.spec.ts&#10;utils/        -> helper umum"
        class="mt-3 w-full rounded-xl border border-gray-200 px-4 py-3 font-mono text-xs leading-relaxed text-gray-800 placeholder-gray-400 focus:border-gray-400 focus:outline-none read-only:bg-gray-50"
      />
    </section>

    <p v-if="errorMessage" class="flex items-start gap-2 text-sm text-red-600" role="alert">
      <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ errorMessage }}
    </p>

    <!-- Aksi (OWNER) -->
    <div v-if="isOwner" class="flex flex-wrap items-center justify-between gap-3">
      <button
        v-if="setup.configured"
        type="button"
        class="rounded-xl px-4 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
        :disabled="isSaving || isDeleting"
        @click="isConfirmingReset = true"
      >
        Hapus setup
      </button>
      <span v-else />
      <button
        type="button"
        class="rounded-xl bg-gray-900 px-6 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
        :disabled="!canSave"
        @click="save"
      >
        {{ isSaving ? 'Menyimpan...' : setup.configured ? 'Simpan perubahan' : 'Simpan setup' }}
      </button>
    </div>

    <!-- Konfirmasi hapus -->
    <div
      v-if="isConfirmingReset"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="isConfirmingReset = false"
    >
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="reset-setup-title">
        <h3 id="reset-setup-title" class="text-lg font-bold text-gray-900">Hapus setup automation?</h3>
        <p class="mt-2 text-sm text-gray-500">
          Semua anggota tidak bisa generate sampai setup baru dibuat. Riwayat generate yang sudah ada tetap tersimpan.
        </p>
        <div class="mt-6 flex justify-end gap-3">
          <button type="button" class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100" @click="isConfirmingReset = false">
            Batal
          </button>
          <button
            type="button"
            class="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
            :disabled="isDeleting"
            @click="resetSetup"
          >
            {{ isDeleting ? 'Menghapus...' : 'Hapus' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<!-- frontend/src/modules/automation/components/AutomationGenerateTab.vue -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import axios from 'axios';
import { automationService } from '../services/automation.service';
import type {
  AutomationGeneration,
  AutomationGenerationSummary,
  AutomationSetup,
  AutomationUsage,
} from '../types/automation.types';
import { describeAutomationError, describeGenerationFailure, type DescribedError } from '../utils/automationErrors';
import { generateBlockReason, tierLabel, usageText } from '../utils/automationGate';
import { setupSummary } from '../utils/automationOptions';
import { isActiveStatus, pollUntilFinished } from '../utils/automationPolling';
import { tokenStorage } from '@/shared/services/tokenStorage';
import TestCasePicker from './TestCasePicker.vue';
import GenerationProgress from './GenerationProgress.vue';
import GenerationResult from './GenerationResult.vue';
import GenerationHistory from './GenerationHistory.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';

// Alur: pilih test case -> Generate (POST 202) -> polling status -> hasil / pesan error. Proses AI berjalan di
// server (bisa puluhan detik); kalau user pindah halaman, hasilnya tetap ada di riwayat dan polling dilanjutkan
// saat kembali. Kegagalan sisi AI SELALU tampil sbg pesan umum (lihat utils/automationErrors.ts).
const props = defineProps<{
  projectId: string;
  setup: AutomationSetup;
  usage: AutomationUsage;
  isOwner: boolean;
}>();

const emit = defineEmits<{
  (e: 'usage-changed'): void;
  (e: 'go-setup'): void;
}>();

const selected = ref<string[]>([]);
const isSubmitting = ref(false);
const submitError = ref<DescribedError | null>(null);

// Job milik user yang sedang dipantau.
const activeGeneration = ref<{ id: string; status: AutomationGeneration['status'] } | null>(null);
const elapsedMs = ref(0);
const pollNotice = ref('');
let pollToken = 0;
let elapsedTimer: ReturnType<typeof setInterval> | undefined;
let unmounted = false;

// Hasil yang sedang ditampilkan: sukses (detail + berkas) atau gagal (pesan).
const result = ref<AutomationGeneration | null>(null);
const failureMessage = ref('');

const history = ref<AutomationGenerationSummary[]>([]);
const isLoadingHistory = ref(true);

const isBusy = computed(() => isSubmitting.value || activeGeneration.value !== null);
const blockReason = computed(() =>
  generateBlockReason({
    setupConfigured: props.setup.configured,
    aiEnabled: props.usage.aiEnabled,
    selectedCount: selected.value.length,
    maxPerGeneration: props.usage.maxTestCasesPerGeneration,
    dailyLimit: props.usage.dailyLimit,
    usedToday: props.usage.usedToday,
    isBusy: isBusy.value,
  })
);
const canGenerate = computed(() => blockReason.value === null);
// Alasan yang layak ditampilkan di bawah tombol (bukan sekadar "belum memilih").
const visibleBlockReason = computed(() =>
  blockReason.value !== null && selected.value.length === 0 && props.setup.configured && props.usage.aiEnabled && !isBusy.value
    ? null
    : blockReason.value
);

// ---------- Riwayat ----------

async function loadHistory() {
  try {
    history.value = await automationService.listGenerations(props.projectId);
  } catch {
    // Tidak fatal: riwayat hanya pelengkap; alur generate tetap jalan.
  } finally {
    isLoadingHistory.value = false;
  }
}

/** Kembali ke halaman saat ada job milik sendiri yang masih berjalan -> lanjutkan memantau. */
function resumeIfActive() {
  const me = tokenStorage.getTokenSubject();
  if (!me || activeGeneration.value) return;
  const running = history.value.find((item) => item.requestedBy === me && isActiveStatus(item.status));
  if (running) {
    void startPolling(running.id, running.status, running.createdAt);
  }
}

// ---------- Polling ----------

function stopElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer);
    elapsedTimer = undefined;
  }
}

// Job BARU: mulai dari sekarang. Job yang DILANJUTKAN (kembali ke halaman): memakai createdAt dari server, yang berupa
// LocalDateTime tanpa zona waktu -- dibaca sebagai waktu lokal browser, jadi dibatasi supaya selisih zona waktu
// server vs browser tidak menampilkan waktu berjalan yang aneh.
const MAX_RESUMED_ELAPSED_MS = 10 * 60 * 1000;

function startElapsedTimer(createdAt: string | null) {
  stopElapsedTimer();
  let startedAt = Date.now();
  if (createdAt) {
    const parsed = new Date(createdAt).getTime();
    if (!Number.isNaN(parsed)) {
      startedAt = Date.now() - Math.min(MAX_RESUMED_ELAPSED_MS, Math.max(0, Date.now() - parsed));
    }
  }
  elapsedMs.value = Date.now() - startedAt;
  elapsedTimer = setInterval(() => {
    elapsedMs.value = Date.now() - startedAt;
  }, 1000);
}

async function startPolling(id: string, status: AutomationGeneration['status'], createdAt: string | null) {
  const token = ++pollToken;
  activeGeneration.value = { id, status };
  result.value = null;
  failureMessage.value = '';
  pollNotice.value = '';
  startElapsedTimer(createdAt);

  const outcome = await pollUntilFinished({
    fetchOne: () => automationService.getGeneration(props.projectId, id),
    sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    now: () => Date.now(),
    isCancelled: () => unmounted || token !== pollToken,
    onUpdate: (generation) => {
      if (token === pollToken && activeGeneration.value) activeGeneration.value.status = generation.status;
    },
    // 403/404 = job/project tidak bisa diakses lagi: tidak ada gunanya mencoba ulang.
    isFatal: (error) => axios.isAxiosError(error) && [403, 404].includes(error.response?.status ?? 0),
  });

  if (outcome.kind === 'cancelled' || token !== pollToken) return;

  stopElapsedTimer();
  activeGeneration.value = null;

  if (outcome.kind === 'finished') {
    if (outcome.generation.status === 'SUCCEEDED') {
      result.value = outcome.generation;
    } else {
      failureMessage.value = describeGenerationFailure(outcome.generation.errorCode, outcome.generation.errorMessage);
    }
  } else if (outcome.kind === 'timeout') {
    pollNotice.value = 'Proses masih berjalan di server dan memakan waktu lebih lama dari biasa. Cek riwayat beberapa saat lagi.';
  } else {
    pollNotice.value = 'Tidak dapat memperbarui status saat ini. Hasilnya tetap akan muncul di riwayat.';
  }

  emit('usage-changed');
  void loadHistory();
}

// ---------- Aksi ----------

async function submit() {
  if (!canGenerate.value) return;
  isSubmitting.value = true;
  submitError.value = null;
  result.value = null;
  failureMessage.value = '';
  pollNotice.value = '';
  try {
    const created = await automationService.generate(props.projectId, selected.value);
    emit('usage-changed');
    void loadHistory();
    void startPolling(created.id, created.status, null);
  } catch (err) {
    if (axios.isAxiosError(err)) {
      submitError.value = describeAutomationError(err.response?.status, err.response?.data, 'Gagal memulai generate, coba lagi.');
      // Sudah ada job berjalan (mis. dibuka dari tab lain): ambil dan pantau job itu.
      if (submitError.value.kind === 'in-progress') {
        await loadHistory();
        resumeIfActive();
      }
    } else {
      submitError.value = { kind: 'generic', message: 'Tidak dapat terhubung ke server.', errorCode: null };
    }
  } finally {
    isSubmitting.value = false;
  }
}

async function viewGeneration(id: string) {
  submitError.value = null;
  pollNotice.value = '';
  try {
    const detail = await automationService.getGeneration(props.projectId, id);
    if (detail.status === 'SUCCEEDED') {
      result.value = detail;
      failureMessage.value = '';
    }
  } catch (err) {
    submitError.value = axios.isAxiosError(err)
      ? describeAutomationError(err.response?.status, err.response?.data, 'Gagal memuat hasil generate.')
      : { kind: 'generic', message: 'Tidak dapat terhubung ke server.', errorCode: null };
  }
}

onMounted(async () => {
  await loadHistory();
  resumeIfActive();
});

onBeforeUnmount(() => {
  unmounted = true;
  pollToken++; // hentikan polling yang sedang berjalan
  stopElapsedTimer();
});
</script>

<template>
  <div class="space-y-5">
    <!-- Ringkasan setup + pemakaian -->
    <div class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 px-5 py-3.5">
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-sm">
        <span class="text-gray-500">Setup:</span>
        <span v-if="setup.configured" class="font-semibold text-gray-900">{{ setupSummary(setup) }}</span>
        <span v-else class="font-medium text-amber-700">Belum diatur</span>
        <button v-if="isOwner || !setup.configured" type="button" class="text-xs font-medium text-gray-500 underline-offset-2 hover:text-gray-800 hover:underline" @click="emit('go-setup')">
          {{ setup.configured ? 'Ubah' : 'Atur sekarang' }}
        </button>
      </div>
      <div class="flex flex-wrap items-center gap-3 text-xs text-gray-500">
        <span class="rounded-full bg-gray-100 px-2.5 py-1 font-semibold text-gray-700">{{ tierLabel(usage.tier) }}</span>
        <span>{{ usageText(usage) }}</span>
        <span>Maks. {{ usage.maxTestCasesPerGeneration }} test case per generate</span>
      </div>
    </div>

    <!-- Setup belum ada -->
    <div v-if="!setup.configured" class="rounded-2xl border border-dashed border-gray-300 px-6 py-12 text-center">
      <p class="text-sm font-semibold text-gray-900">Struktur automation belum diatur</p>
      <p class="mx-auto mt-1 max-w-md text-sm text-gray-500">
        {{ isOwner ? 'Pilih framework, bahasa, dan pola struktur di tab Setup sebelum menggenerate kode.' : 'Minta Owner project untuk mengatur framework, bahasa, dan pola struktur di tab Setup.' }}
      </p>
      <button v-if="isOwner" type="button" class="mt-4 rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800" @click="emit('go-setup')">
        Buka Setup
      </button>
    </div>

    <template v-else>
      <!-- AI belum diaktifkan -->
      <div v-if="!usage.aiEnabled" class="flex items-start gap-2.5 rounded-xl border border-gray-200 bg-gray-50 px-5 py-3.5 text-sm text-gray-600" role="status">
        <AlertCircleIcon :size="16" class="mt-0.5 shrink-0 text-gray-400" />
        <p>
          <span class="font-semibold text-gray-800">Fitur generate belum diaktifkan.</span>
          Kamu tetap bisa mengatur setup struktur; tombol Generate akan aktif setelah layanan AI tersedia.
        </p>
      </div>

      <TestCasePicker
        v-model:selected="selected"
        :project-id="projectId"
        :max="usage.maxTestCasesPerGeneration"
        :disabled="isBusy"
      />

      <!-- Pemberitahuan privasi (tampil SEBELUM generate) -->
      <div class="flex items-start gap-2.5 rounded-xl border border-blue-100 bg-blue-50/60 px-5 py-3.5 text-sm text-blue-900" role="note">
        <SparklesIcon :size="16" class="mt-0.5 shrink-0" />
        <p>
          Isi test case yang dipilih (judul, pre-condition, langkah, expected results, dan seterusnya) akan <span class="font-semibold">dikirim ke layanan AI pihak ketiga</span>
          untuk menyusun kode. Jangan menaruh password, token, atau data pribadi asli di test case.
        </p>
      </div>

      <!-- Aksi -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="min-h-5 text-sm text-gray-500" aria-live="polite">{{ visibleBlockReason }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-xl bg-gray-900 px-6 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
          :disabled="!canGenerate"
          @click="submit"
        >
          <SparklesIcon :size="16" />
          {{ isSubmitting ? 'Mengirim...' : selected.length > 0 ? `Generate kode (${selected.length})` : 'Generate kode' }}
        </button>
      </div>

      <!-- Error saat memulai -->
      <div
        v-if="submitError"
        class="flex items-start gap-2.5 rounded-xl border px-5 py-3.5 text-sm"
        :class="submitError.kind === 'plan-limit' ? 'border-amber-200 bg-amber-50 text-amber-800' : 'border-red-200 bg-red-50 text-red-700'"
        role="alert"
      >
        <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />
        <p>{{ submitError.message }}</p>
      </div>

      <!-- Progres -->
      <GenerationProgress v-if="activeGeneration" :status="activeGeneration.status" :elapsed-ms="elapsedMs" />

      <p v-if="pollNotice" class="rounded-xl border border-amber-200 bg-amber-50 px-5 py-3.5 text-sm text-amber-800" role="status">
        {{ pollNotice }}
      </p>

      <!-- Gagal -->
      <div v-if="failureMessage" class="rounded-2xl border border-red-200 bg-red-50/50 px-6 py-5" role="alert">
        <p class="flex items-start gap-2 text-sm font-medium text-red-700">
          <AlertCircleIcon :size="16" class="mt-0.5 shrink-0" />{{ failureMessage }}
        </p>
        <button
          type="button"
          class="mt-3 rounded-xl border border-red-200 bg-white px-4 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!canGenerate"
          @click="submit"
        >
          Coba lagi
        </button>
        <p v-if="selected.length === 0" class="mt-2 text-xs text-red-600/70">Pilih test case lagi untuk mencoba ulang.</p>
      </div>

      <!-- Hasil -->
      <GenerationResult v-if="result" :project-id="projectId" :generation="result" />
    </template>

    <GenerationHistory :items="history" :is-loading="isLoadingHistory" :viewing-id="result?.id ?? null" @view="viewGeneration" />
  </div>
</template>

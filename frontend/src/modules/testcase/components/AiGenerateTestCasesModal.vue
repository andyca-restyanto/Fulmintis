<!-- frontend/src/modules/testcase/components/AiGenerateTestCasesModal.vue -->
<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue';
import { testCaseAiService } from '../services/testCaseAi.service';
import type { CommitTestCasesResult, TestCaseAiUsage } from '../types/testCaseAi.types';
import {
  DRAFT_NOT_SAVED_NOTE,
  IN_PROGRESS_HINT,
  PRIVACY_NOTICE,
  SANDBOX_WARNING,
  pendingBannerText,
  progressText,
  quotaLabel,
  regenerateWarning,
  requirementCounterText,
} from '../utils/testCaseAiGate';
import { saveButtonLabel } from '../utils/testCaseAiDraft';
import { useTestCaseAiGeneration } from '../composables/useTestCaseAiGeneration';
import { useToast } from '@/shared/composables/useToast';
import AiDraftCard from './AiDraftCard.vue';
import AiJobProgress from '@/shared/components/AiJobProgress.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';

// Generate test case dgn AI, 3 langkah: Requirement -> Proses -> Review & Simpan. Hasil AI adalah DRAFT (belum tersimpan): user
// mereview/mengedit, baru menyimpannya (semua atau tidak sama sekali). Semua batas (kuota harian, jumlah draft, panjang requirement)
// dibaca dari `usage` yang dikirim backend. Kegagalan sisi AI SELALU tampil sbg pesan umum (lihat shared/utils/aiErrors.ts).
//
// Seluruh alur (status, polling, simpan, error) ada di composable useTestCaseAiGeneration -- komponen ini hanya template. Komponen
// tetap ter-mount walau jendela ditutup, jadi proses yang sedang berjalan TIDAK berhenti saat user menutup jendela: begitu selesai,
// user diberi tahu lewat toast dan drafnya ada saat jendela dibuka lagi. Kalau halamannya ditinggalkan, proses tetap jalan di server
// dan draftnya bisa dilanjutkan lewat GET /pending.
const props = defineProps<{
  open: boolean;
  projectId: string;
  folderId: string;
  folderName: string;
  usage: TestCaseAiUsage | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved', result: CommitTestCasesResult): void;
  /** Jatah harian / status fitur mungkin berubah: panel memuat ulang usage. */
  (e: 'usage-changed'): void;
}>();

const { pushToast } = useToast();

const STEPS = ['Requirement', 'Proses', 'Review & Simpan'] as const;

const flow = useTestCaseAiGeneration(
  {
    projectId: () => props.projectId,
    folderId: () => props.folderId,
    folderName: () => props.folderName,
    usage: () => props.usage,
    isOpen: () => props.open,
  },
  {
    onClose: () => emit('close'),
    onSaved: (result) => emit('saved', result),
    onUsageChanged: () => emit('usage-changed'),
    toast: pushToast,
  },
  testCaseAiService,
);

const {
  phase,
  requirement,
  count,
  includeNegative,
  isSubmitting,
  submitError,
  failureMessage,
  activeStatus,
  elapsedMs,
  drafts,
  pending,
  isSaving,
  saveError,
  confirming,
  blockReason,
  submitGate,
  overLimit,
  activeStepIndex,
  target,
  saveGate,
  selectedCount,
  selection,
  countNotice,
  selectedDuplicates,
  regenerateAllowed,
  isBusy,
  submit,
  resumePending,
  dismissPending,
  cancelConfirm,
  resetToInput,
  setSelectAll,
  setExpandAll,
  discardDraft,
  save,
  askRegenerate,
  confirmRegenerate,
  discardAndClose,
  requestClose,
} = flow;

// Jumlah draft dijaga di rentang dari server; saat jendela dibuka dalam keadaan idle, cek draft terakhir yang belum disimpan.
watch(() => props.usage, () => flow.syncCount(), { immediate: true });
watch(() => props.open, (isOpen) => flow.onOpenChanged(isOpen), { immediate: true });
watch(() => props.projectId, () => flow.onProjectChanged());

onBeforeUnmount(() => flow.dispose());
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-8"
    @click.self="requestClose"
  >
    <div
      class="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ai-generate-title"
    >
      <!-- Header -->
      <div class="flex items-start justify-between border-b border-gray-100 px-6 py-5">
        <div>
          <h3 id="ai-generate-title" class="flex items-center gap-2 text-lg font-bold text-gray-900">
            <SparklesIcon :size="18" />
            Generate Test Case dengan AI
          </h3>
          <p class="mt-0.5 text-sm text-gray-500">
            Ke folder <span class="font-semibold text-gray-700">{{ phase === 'review' ? target.folderName : folderName }}</span>
          </p>
        </div>
        <button
          type="button"
          class="text-gray-400 hover:text-gray-600 disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="isBusy"
          aria-label="Tutup"
          @click="requestClose"
        >
          <XIcon :size="20" />
        </button>
      </div>

      <!-- Indikator langkah -->
      <ol class="flex items-center gap-2 border-b border-gray-100 px-6 py-3 text-xs font-medium">
        <template v-for="(label, index) in STEPS" :key="label">
          <li
            class="flex items-center gap-2"
            :class="index <= activeStepIndex ? 'text-gray-900' : 'text-gray-400'"
            :aria-current="index === activeStepIndex ? 'step' : undefined"
          >
            <span
              class="flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold"
              :class="index <= activeStepIndex ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-400'"
            >
              {{ index + 1 }}
            </span>
            {{ label }}
          </li>
          <li v-if="index < STEPS.length - 1" class="h-px w-6 bg-gray-200" aria-hidden="true" />
        </template>
      </ol>

      <!-- Isi -->
      <div class="flex-1 overflow-y-auto px-6 py-5">
        <!-- ===== Langkah 1: Requirement ===== -->
        <div v-if="phase === 'input'" class="space-y-4">
          <p v-if="usage?.sandbox" class="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800" role="note">
            {{ SANDBOX_WARNING }}
          </p>

          <div
            v-if="pending"
            class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50 px-4 py-3"
          >
            <p class="text-sm text-blue-900">
              {{ pendingBannerText(pending) }} Lanjutkan review tanpa memakai jatah generate baru.
            </p>
            <div class="flex shrink-0 gap-2">
              <button
                type="button"
                class="rounded-xl bg-blue-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-blue-700"
                @click="resumePending"
              >
                Lanjutkan review
              </button>
              <button
                type="button"
                class="rounded-xl px-3 py-2 text-sm font-medium text-blue-800 hover:bg-blue-100"
                @click="dismissPending"
              >
                Abaikan
              </button>
            </div>
          </div>

          <p v-if="blockReason" class="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-700" role="status">
            {{ blockReason }}
          </p>

          <div>
            <label for="ai-requirement" class="mb-2 block text-sm font-semibold text-gray-900">Requirement</label>
            <textarea
              id="ai-requirement"
              v-model="requirement"
              rows="8"
              placeholder="Tempel requirement, user story, atau deskripsi fitur yang ingin dibuatkan test case-nya..."
              class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100 disabled:bg-gray-50"
              :class="{ 'border-red-500': overLimit }"
              :disabled="isSubmitting || blockReason !== null"
              aria-describedby="ai-requirement-counter"
            />
            <div class="mt-1.5 flex items-start justify-between gap-3">
              <p class="text-xs text-gray-400">{{ PRIVACY_NOTICE }}</p>
              <span
                id="ai-requirement-counter"
                class="shrink-0 text-xs tabular-nums"
                :class="overLimit ? 'font-semibold text-red-600' : 'text-gray-400'"
              >
                {{ usage ? requirementCounterText(requirement, usage.maxRequirementChars) : '' }}
              </span>
            </div>
          </div>

          <div class="flex flex-wrap items-end gap-x-8 gap-y-4">
            <div>
              <label for="ai-count" class="mb-2 block text-sm font-semibold text-gray-900">Jumlah draft</label>
              <input
                id="ai-count"
                v-model.number="count"
                type="number"
                min="1"
                :max="usage?.maxDraftsPerGeneration"
                class="w-24 rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100 disabled:bg-gray-50"
                :disabled="isSubmitting || blockReason !== null"
                aria-describedby="ai-count-hint"
              />
              <p id="ai-count-hint" class="mt-1 text-xs text-gray-400">Maksimal {{ usage?.maxDraftsPerGeneration ?? '-' }} per generate</p>
            </div>

            <label class="flex cursor-pointer items-center gap-2 pb-6 text-sm text-gray-700">
              <input
                v-model="includeNegative"
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-gray-300"
                :disabled="isSubmitting || blockReason !== null"
              />
              Sertakan skenario negatif &amp; batas
            </label>
          </div>

          <p v-if="usage" class="text-xs text-gray-500">{{ quotaLabel(usage) }}</p>

          <div v-if="submitError" class="rounded-xl border border-red-200 bg-red-50 px-4 py-3" role="alert">
            <p class="text-sm text-red-700">{{ submitError.message }}</p>
            <p v-if="submitError.kind === 'in-progress'" class="mt-1 text-xs text-red-600">{{ IN_PROGRESS_HINT }}</p>
          </div>
        </div>

        <!-- ===== Langkah 2: Proses ===== -->
        <div v-else-if="phase === 'generating'" class="py-4">
          <AiJobProgress
            :status="activeStatus === 'QUEUED' ? 'QUEUED' : 'RUNNING'"
            :elapsed-ms="elapsedMs"
            :message="progressText(activeStatus, elapsedMs)"
            footnote="Proses berjalan di server. Kamu boleh menutup jendela ini; kamu akan diberi tahu saat draft siap, dan draftnya muncul saat jendela dibuka lagi."
          />
        </div>

        <!-- ===== Gagal ===== -->
        <div v-else-if="phase === 'failed'" class="space-y-3 py-2">
          <div class="rounded-xl border border-red-200 bg-red-50 px-4 py-4" role="alert">
            <p class="text-sm font-semibold text-red-800">Generate gagal</p>
            <p class="mt-1 text-sm text-red-700">{{ failureMessage }}</p>
          </div>
          <p class="text-xs text-gray-400">Requirement Anda tidak hilang. Generate yang gagal karena layanan AI tidak memakai jatah harian.</p>
        </div>

        <!-- ===== Langkah 3: Review & Simpan ===== -->
        <div v-else class="space-y-4">
          <p class="rounded-xl bg-gray-50 px-4 py-3 text-sm text-gray-600">{{ DRAFT_NOT_SAVED_NOTE }}</p>

          <p v-if="countNotice" class="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800" role="status">
            {{ countNotice }}
          </p>

          <div v-if="drafts.length > 0" class="flex flex-wrap items-center justify-between gap-3">
            <label class="flex cursor-pointer items-center gap-2 text-sm font-medium text-gray-700">
              <input
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-gray-300"
                :checked="selection === 'all'"
                :indeterminate="selection === 'some'"
                :disabled="isSaving"
                @change="setSelectAll(selection !== 'all')"
              />
              Pilih semua
            </label>
            <div class="flex items-center gap-3 text-sm">
              <span class="text-gray-500">{{ selectedCount }} dari {{ drafts.length }} dipilih</span>
              <button type="button" class="font-medium text-gray-600 hover:text-gray-900" @click="setExpandAll(true)">Buka semua</button>
              <button type="button" class="font-medium text-gray-600 hover:text-gray-900" @click="setExpandAll(false)">Ciutkan semua</button>
            </div>
          </div>

          <p v-if="selectedDuplicates > 0" class="text-xs text-amber-700">
            {{ selectedDuplicates }} draft yang dipilih berjudul sama dengan test case yang sudah ada di folder ini. Tetap bisa disimpan.
          </p>

          <div v-if="saveError" class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
            {{ saveError }}
          </div>

          <div v-if="drafts.length > 0" class="space-y-3">
            <AiDraftCard
              v-for="(item, index) in drafts"
              :key="item.tempId"
              v-model="drafts[index]"
              :disabled="isSaving"
              @remove="discardDraft"
            />
          </div>
          <div v-else class="rounded-xl border border-dashed border-gray-300 px-4 py-8 text-center text-sm text-gray-500">
            Semua draft sudah dibuang. Generate ulang untuk membuat draft baru, atau tutup jendela ini.
          </div>
        </div>
      </div>

      <!-- Konfirmasi (generate ulang / batal) -->
      <div v-if="confirming !== 'none'" class="border-t border-amber-200 bg-amber-50 px-6 py-4" role="alertdialog">
        <p class="text-sm text-amber-900">
          <template v-if="confirming === 'regenerate'">{{ regenerateWarning(usage) }}</template>
          <template v-else>
            Editan Anda dan draft yang sudah Anda buang tidak akan tersimpan. Draft asli dari AI masih bisa dibuka lagi nanti lewat
            "Lanjutkan review" selama belum disimpan.
          </template>
        </p>
        <div class="mt-3 flex justify-end gap-2">
          <button
            type="button"
            class="rounded-xl px-4 py-2 text-sm font-medium text-gray-700 hover:bg-amber-100"
            @click="cancelConfirm"
          >
            Kembali
          </button>
          <button
            v-if="confirming === 'regenerate'"
            type="button"
            class="rounded-xl bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
            :disabled="!regenerateAllowed"
            @click="confirmRegenerate"
          >
            Ya, generate ulang
          </button>
          <button
            v-else
            type="button"
            class="rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
            @click="discardAndClose"
          >
            Ya, buang &amp; tutup
          </button>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 px-6 py-4">
        <div class="min-w-0 text-xs text-gray-500">
          <template v-if="phase === 'input' && !submitGate.ok && submitGate.reason && requirement.trim() !== ''">{{ submitGate.reason }}</template>
          <template v-else-if="phase === 'review' && !saveGate.ok">{{ saveGate.reason }}</template>
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2">
          <!-- input -->
          <template v-if="phase === 'input'">
            <button
              type="button"
              class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isSubmitting"
              @click="requestClose"
            >
              Batal
            </button>
            <button
              type="button"
              class="flex items-center gap-2 rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
              :disabled="!submitGate.ok"
              :title="submitGate.reason ?? undefined"
              @click="submit"
            >
              <SparklesIcon :size="15" />
              {{ isSubmitting ? 'Memulai...' : 'Generate' }}
            </button>
          </template>

          <!-- generating -->
          <button
            v-else-if="phase === 'generating'"
            type="button"
            class="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
            @click="requestClose"
          >
            Tutup (proses tetap berjalan)
          </button>

          <!-- failed -->
          <template v-else-if="phase === 'failed'">
            <button
              type="button"
              class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100"
              @click="requestClose"
            >
              Tutup
            </button>
            <button
              type="button"
              class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
              @click="resetToInput(true)"
            >
              Coba lagi
            </button>
          </template>

          <!-- review -->
          <template v-else>
            <button
              type="button"
              class="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isSaving || !regenerateAllowed"
              :title="regenerateAllowed ? undefined : 'Jatah generate harian sudah habis.'"
              @click="askRegenerate"
            >
              Generate ulang
            </button>
            <button
              type="button"
              class="rounded-xl px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isSaving"
              @click="requestClose"
            >
              Batal
            </button>
            <button
              type="button"
              class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
              :disabled="!saveGate.ok || isSaving"
              @click="save"
            >
              {{ isSaving ? 'Menyimpan...' : saveButtonLabel(drafts, target.folderName) }}
            </button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

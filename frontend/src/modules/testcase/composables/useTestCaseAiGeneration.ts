// frontend/src/modules/testcase/composables/useTestCaseAiGeneration.ts
// Orkestrasi "Generate Test Case dengan AI": mesin status (input -> proses -> review / gagal), polling, pemulihan draft yang
// tertunda, penyimpanan, dan penanganan setiap jenis error dari backend. Dipisah dari komponen supaya SELURUH alur bisa diuji di
// Node: service, timer, dan callback diinjeksi (tidak ada akses langsung ke apiClient/DOM di sini). Keputusan murni (gerbang, validasi,
// pemetaan error) ada di utils/testCaseAiGate.ts dan utils/testCaseAiDraft.ts; berkas ini hanya merangkainya.
import { computed, ref } from 'vue';
import axios from 'axios';
import type {
  CommitTestCasesRequest,
  CommitTestCasesResult,
  GenerateTestCasesRequest,
  TestCaseAiGeneration,
  TestCaseAiGenerationCreated,
  TestCaseAiGenerationStatus,
  TestCaseAiUsage,
  TestCaseDraft,
} from '../types/testCaseAi.types';
import {
  POLL_TIMEOUT_MESSAGE,
  aiBlockReason,
  canRegenerate,
  canSubmit,
  clampCount,
  commitTarget,
  draftCountNotice,
  extractCommitErrors,
  isDraftGoneCode,
  requirementLength,
  toGenerateRequest,
} from '../utils/testCaseAiGate';
import {
  applyCommitErrors,
  canSave,
  countDraftsWithServerErrors,
  hasEdits,
  removeDraft,
  selectedDrafts,
  selectionState,
  setAllSelected,
  toCommitItems,
  toEditableDrafts,
  type EditableDraft,
} from '../utils/testCaseAiDraft';
import { describeAiError, describeGenerationFailure, type DescribedAiError } from '../../../shared/utils/aiErrors';
import { isFatalPollStatus, pollUntilFinished } from '../../../shared/utils/aiPolling';

/** Bagian service yang dipakai (testCaseAiService memenuhinya; di test diganti service palsu). */
export interface AiGenerationService {
  generate(projectId: string, payload: GenerateTestCasesRequest): Promise<TestCaseAiGenerationCreated>;
  getGeneration(projectId: string, generationId: string): Promise<TestCaseAiGeneration>;
  getPending(projectId: string): Promise<TestCaseAiGeneration | null>;
  commit(projectId: string, generationId: string, payload: CommitTestCasesRequest): Promise<CommitTestCasesResult>;
}

/** Nilai yang berubah-ubah (props komponen) dibaca lewat fungsi supaya selalu terbaru. */
export interface AiGenerationContext {
  projectId: () => string;
  folderId: () => string;
  folderName: () => string;
  usage: () => TestCaseAiUsage | null;
  isOpen: () => boolean;
}

export interface AiGenerationHooks {
  onClose: () => void;
  onSaved: (result: CommitTestCasesResult) => void;
  /** Jatah harian / status fitur mungkin berubah: pemilik tampilan memuat ulang usage. */
  onUsageChanged: () => void;
  toast: (input: { type: 'success' | 'error' | 'info'; message: string }) => void;
}

export interface AiGenerationTiming {
  now: () => number;
  sleep: (ms: number) => Promise<void>;
  setInterval: (callback: () => void, ms: number) => unknown;
  clearInterval: (handle: unknown) => void;
}

export const realTiming: AiGenerationTiming = {
  now: () => Date.now(),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  setInterval: (callback, ms) => setInterval(callback, ms),
  clearInterval: (handle) => clearInterval(handle as ReturnType<typeof setInterval>),
};

export type AiGenerationPhase = 'input' | 'generating' | 'review' | 'failed';
export type AiConfirming = 'none' | 'regenerate' | 'cancel';

export function useTestCaseAiGeneration(
  ctx: AiGenerationContext,
  hooks: AiGenerationHooks,
  service: AiGenerationService,
  timing: AiGenerationTiming = realTiming,
) {
  // ---------- state ----------
  const phase = ref<AiGenerationPhase>('input');
  const requirement = ref('');
  // Kosong sampai `usage` tiba; syncCount() lalu mengisinya dgn batas tier (sama dgn bawaan backend bila jumlah tidak diisi).
  const count = ref<number | ''>('');
  const includeNegative = ref(true);
  const isSubmitting = ref(false);
  const submitError = ref<DescribedAiError | null>(null);
  const failureMessage = ref('');

  const activeStatus = ref<TestCaseAiGenerationStatus>('QUEUED');
  const elapsedMs = ref(0);

  const generation = ref<TestCaseAiGeneration | null>(null);
  const originals = ref<TestCaseDraft[]>([]);
  const drafts = ref<EditableDraft[]>([]);
  const pending = ref<TestCaseAiGeneration | null>(null);

  const isSaving = ref(false);
  const saveError = ref('');
  const confirming = ref<AiConfirming>('none');

  let pollToken = 0;
  let elapsedTimer: unknown;
  let lastPolling: Promise<void> = Promise.resolve();

  // ---------- turunan ----------
  const blockReason = computed(() => aiBlockReason(ctx.usage(), false));
  const submitGate = computed(() => canSubmit(ctx.usage(), requirement.value, isSubmitting.value));
  const overLimit = computed(() => {
    const usage = ctx.usage();
    return usage !== null && requirementLength(requirement.value) > usage.maxRequirementChars;
  });
  const activeStepIndex = computed(() => (phase.value === 'input' ? 0 : phase.value === 'review' ? 2 : 1));
  const target = computed(() =>
    generation.value
      ? commitTarget(generation.value, { folderId: ctx.folderId(), folderName: ctx.folderName() })
      : { folderId: ctx.folderId(), folderName: ctx.folderName() },
  );
  const saveGate = computed(() => canSave(drafts.value));
  const selectedCount = computed(() => selectedDrafts(drafts.value).length);
  const selection = computed(() => selectionState(drafts.value));
  const hasUnsavedEdits = computed(() => hasEdits(drafts.value, originals.value));
  const countNotice = computed(() => (generation.value ? draftCountNotice(generation.value) : null));
  const selectedDuplicates = computed(() => selectedDrafts(drafts.value).filter((d) => d.duplicateOfExisting).length);
  const regenerateAllowed = computed(() => canRegenerate(ctx.usage()));
  const isBusy = computed(() => isSubmitting.value || isSaving.value);

  // ---------- pengaturan awal & siklus hidup ----------

  /** Jumlah draft selalu dijaga di rentang 1..batas dari server (batas bisa berubah antar tier). */
  function syncCount() {
    const usage = ctx.usage();
    if (usage) {
      count.value = clampCount(count.value === '' ? usage.maxDraftsPerGeneration : count.value, usage.maxDraftsPerGeneration);
    }
  }

  /** Dipanggil saat jendela dibuka/ditutup: bila dibuka dalam keadaan idle, cek draft terakhir yang belum disimpan. */
  function onOpenChanged(isOpen: boolean) {
    if (isOpen && phase.value === 'input') {
      void loadPending();
    }
  }

  /** Pindah project: draft & job milik project lain tidak boleh terbawa. */
  function onProjectChanged() {
    resetToInput(false);
    pending.value = null;
  }

  function dispose() {
    pollToken++;
    stopElapsed();
  }

  async function loadPending() {
    try {
      pending.value = await service.getPending(ctx.projectId());
    } catch {
      // Non-fatal: tawaran "lanjutkan draft" hanya tidak muncul.
      pending.value = null;
    }
  }

  function dismissPending() {
    pending.value = null;
  }

  function cancelConfirm() {
    confirming.value = 'none';
  }

  function stopElapsed() {
    if (elapsedTimer !== undefined) {
      timing.clearInterval(elapsedTimer);
      elapsedTimer = undefined;
    }
  }

  function resetToInput(keepRequirement: boolean) {
    pollToken++; // membatalkan polling yang sedang berjalan
    stopElapsed();
    phase.value = 'input';
    generation.value = null;
    originals.value = [];
    drafts.value = [];
    submitError.value = null;
    failureMessage.value = '';
    saveError.value = '';
    confirming.value = 'none';
    isSaving.value = false;
    elapsedMs.value = 0;
    if (!keepRequirement) {
      requirement.value = '';
    }
  }

  // ---------- generate ----------

  function describeSubmitFailure(err: unknown): DescribedAiError {
    if (axios.isAxiosError(err)) {
      return describeAiError(err.response?.status, err.response?.data, 'Gagal memulai generate. Silakan coba lagi.');
    }
    return { kind: 'generic', message: 'Gagal memulai generate. Silakan coba lagi.', errorCode: null };
  }

  async function submit() {
    const usage = ctx.usage();
    if (!usage || !submitGate.value.ok) return;

    isSubmitting.value = true;
    submitError.value = null;
    pending.value = null;
    try {
      const created = await service.generate(
        ctx.projectId(),
        toGenerateRequest(ctx.folderId(), requirement.value, count.value, includeNegative.value, usage),
      );
      hooks.onUsageChanged(); // jatah harian sudah bertambah
      lastPolling = startPolling(created.id, created.status);
    } catch (err) {
      submitError.value = describeSubmitFailure(err);
      hooks.onUsageChanged(); // mis. batas harian tercapai: tombol di panel ikut nonaktif
      void loadPending(); // draft sebelumnya (kalau ada) masih bisa dilanjutkan
    } finally {
      isSubmitting.value = false;
    }
  }

  async function startPolling(generationId: string, initialStatus: TestCaseAiGenerationStatus) {
    const token = ++pollToken;
    phase.value = 'generating';
    activeStatus.value = initialStatus;

    const startedAt = timing.now();
    elapsedMs.value = 0;
    stopElapsed();
    elapsedTimer = timing.setInterval(() => {
      elapsedMs.value = timing.now() - startedAt;
    }, 1000);

    const outcome = await pollUntilFinished({
      fetchOne: () => service.getGeneration(ctx.projectId(), generationId),
      sleep: timing.sleep,
      now: timing.now,
      isCancelled: () => token !== pollToken,
      onUpdate: (g) => {
        activeStatus.value = g.status;
      },
      isFatal: (error) => axios.isAxiosError(error) && isFatalPollStatus(error.response?.status),
    });

    if (outcome.kind === 'cancelled' || token !== pollToken) return;
    stopElapsed();
    hooks.onUsageChanged();

    if (outcome.kind === 'finished') {
      handleFinished(outcome.generation);
    } else if (outcome.kind === 'timeout') {
      fail(POLL_TIMEOUT_MESSAGE);
    } else {
      fail(
        axios.isAxiosError(outcome.error)
          ? describeAiError(outcome.error.response?.status, outcome.error.response?.data, 'Gagal memuat hasil generate.').message
          : 'Gagal memuat hasil generate.',
      );
    }
  }

  function handleFinished(result: TestCaseAiGeneration) {
    if (result.status === 'SUCCEEDED' && result.drafts.length > 0) {
      enterReview(result);
      if (!ctx.isOpen()) {
        hooks.toast({ type: 'info', message: 'Draft test case siap direview. Buka Generate with AI untuk melanjutkannya.' });
      }
      return;
    }
    fail(
      result.status === 'FAILED'
        ? describeGenerationFailure(result.errorCode, result.errorMessage)
        : 'Generate tidak menghasilkan draft. Silakan coba lagi.',
    );
  }

  function fail(message: string) {
    failureMessage.value = message;
    phase.value = 'failed';
    if (!ctx.isOpen()) {
      hooks.toast({ type: 'error', message });
    }
  }

  function enterReview(result: TestCaseAiGeneration) {
    generation.value = result;
    originals.value = result.drafts;
    drafts.value = toEditableDrafts(result.drafts);
    pending.value = null;
    saveError.value = '';
    confirming.value = 'none';
    phase.value = 'review';
  }

  function resumePending() {
    if (pending.value) {
      enterReview(pending.value);
    }
  }

  // ---------- review ----------

  function setSelectAll(value: boolean) {
    drafts.value = setAllSelected(drafts.value, value);
  }

  function setExpandAll(value: boolean) {
    for (const draft of drafts.value) {
      draft.expanded = value;
    }
  }

  function discardDraft(tempId: string) {
    drafts.value = removeDraft(drafts.value, tempId);
  }

  async function save() {
    const current = generation.value;
    if (!current || isSaving.value || !saveGate.value.ok) return;

    // `index` pada error server = posisi pada daftar yang DIKIRIM (hanya draft tercentang, urutan tampil).
    const submittedIds = selectedDrafts(drafts.value).map((d) => d.tempId);
    isSaving.value = true;
    saveError.value = '';
    try {
      const result = await service.commit(ctx.projectId(), current.id, {
        folderId: target.value.folderId,
        testCases: toCommitItems(drafts.value),
      });
      resetToInput(false);
      hooks.onSaved(result);
    } catch (err) {
      handleSaveFailure(err, submittedIds);
    } finally {
      isSaving.value = false;
    }
  }

  function handleSaveFailure(err: unknown, submittedIds: string[]) {
    if (!axios.isAxiosError(err)) {
      saveError.value = 'Gagal menyimpan test case. Silakan coba lagi.';
      return;
    }
    const data = err.response?.data;
    const described = describeAiError(err.response?.status, data, 'Gagal menyimpan test case. Silakan coba lagi.');

    if (described.errorCode === 'INVALID_DRAFTS') {
      // Tidak ada yang tersimpan; tandai kartu yang bermasalah supaya bisa diperbaiki lalu disimpan lagi.
      drafts.value = applyCommitErrors(drafts.value, submittedIds, extractCommitErrors(data));
      const bad = countDraftsWithServerErrors(drafts.value);
      saveError.value =
        bad > 0
          ? `Server menolak ${bad} draft. Tidak ada test case yang tersimpan; perbaiki kartu yang bertanda merah lalu simpan lagi.`
          : described.message;
      return;
    }
    if (isDraftGoneCode(described.errorCode)) {
      // Sudah tersimpan sebelumnya / kedaluwarsa: draft ini tidak bisa dipakai lagi -> kembali ke langkah awal dengan penjelasan.
      resetToInput(true);
      submitError.value = described;
      void loadPending();
      hooks.onUsageChanged();
      return;
    }
    // Gangguan sementara (jaringan/server): draft dan editan tetap utuh, user boleh mencoba lagi.
    saveError.value = described.message;
  }

  // ---------- generate ulang & batal ----------

  function askRegenerate() {
    confirming.value = 'regenerate';
  }

  async function confirmRegenerate() {
    if (!regenerateAllowed.value) return;
    resetToInput(true);
    await submit();
  }

  function discardAndClose() {
    resetToInput(true);
    hooks.onClose();
  }

  function requestClose() {
    if (isBusy.value) return;
    if (phase.value === 'generating') {
      // Proses tetap berjalan di server DAN di sini; hasilnya diberitahukan lewat toast saat selesai.
      hooks.onClose();
      return;
    }
    if (phase.value === 'review') {
      if (hasUnsavedEdits.value) {
        confirming.value = 'cancel';
        return;
      }
      discardAndClose();
      return;
    }
    resetToInput(true);
    hooks.onClose();
  }

  /** Menunggu polling yang sedang berjalan selesai (dipakai test; aman dipanggil kapan saja). */
  function settled(): Promise<void> {
    return lastPolling;
  }

  return {
    // state
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
    generation,
    originals,
    // turunan
    blockReason,
    submitGate,
    overLimit,
    activeStepIndex,
    target,
    saveGate,
    selectedCount,
    selection,
    hasUnsavedEdits,
    countNotice,
    selectedDuplicates,
    regenerateAllowed,
    isBusy,
    // aksi
    syncCount,
    onOpenChanged,
    onProjectChanged,
    dispose,
    dismissPending,
    cancelConfirm,
    resetToInput,
    submit,
    resumePending,
    setSelectAll,
    setExpandAll,
    discardDraft,
    save,
    askRegenerate,
    confirmRegenerate,
    discardAndClose,
    requestClose,
    settled,
  };
}

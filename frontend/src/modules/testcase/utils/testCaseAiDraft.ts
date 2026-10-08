// frontend/src/modules/testcase/utils/testCaseAiDraft.ts
// Logika murni (tanpa Vue) utk layar review draft AI: draft yang bisa diedit, validasi (aturan yang sama dgn form create),
// konversi ke payload commit, deteksi editan, pemilihan, dan pemetaan error per item dari server.
import type { TestCasePriority, TestCaseScenarioType, TestCaseType } from '../types/testCase.types';
import type { CommitItemError, CommitTestCaseItem, TestCaseDraft } from '../types/testCaseAi.types';
import { serializeSteps, type TestStepRow } from './testStepSerializer';

// Sama dgn batas validasi backend saat commit (TestCaseAiServiceImpl).
export const TITLE_MAX = 255;
export const TEXT_MAX = 20_000;

export type DraftFieldKey = 'title' | 'priority' | 'type' | 'description' | 'objective' | 'precondition' | 'steps';
export type DraftErrors = Partial<Record<DraftFieldKey, string>>;

/** Baris langkah dgn id lokal FE-only (hanya utk :key supaya daftar stabil saat baris dihapus di tengah). */
export interface EditableStep extends TestStepRow {
  id: number;
}

export interface EditableDraft {
  tempId: string;
  /** Dicentang = ikut disimpan. Bawaan: semua tercentang. */
  selected: boolean;
  /** Kartu dibuka utk diedit. Bawaan: diciutkan (15 draft tetap enak dibaca). */
  expanded: boolean;
  title: string;
  priority: TestCasePriority;
  type: TestCaseType;
  scenarioType: TestCaseScenarioType | null;
  description: string;
  objective: string;
  precondition: string;
  steps: EditableStep[];
  duplicateOfExisting: boolean;
  /** Pesan dari server (400 INVALID_DRAFTS) per kolom; hilang saat user mengubah kolom itu. */
  serverErrors: DraftErrors;
}

let stepIdCounter = 0;

export function newStep(action = '', expectedResult = ''): EditableStep {
  return { id: ++stepIdCounter, action, expectedResult };
}

export function toEditableDrafts(drafts: TestCaseDraft[]): EditableDraft[] {
  return drafts.map((draft) => ({
    tempId: draft.tempId,
    selected: true,
    expanded: false,
    title: draft.title,
    priority: draft.priority,
    type: draft.type,
    scenarioType: draft.scenarioType,
    description: draft.description ?? '',
    objective: draft.objective ?? '',
    precondition: draft.precondition ?? '',
    steps: draft.steps.length > 0 ? draft.steps.map((s) => newStep(s.action, s.expected)) : [newStep()],
    duplicateOfExisting: draft.duplicateOfExisting,
    serverErrors: {},
  }));
}

// ---------- Validasi (aturan yang sama dgn membuat test case manual) ----------

export function validateDraft(draft: EditableDraft): DraftErrors {
  const errors: DraftErrors = {};
  const title = draft.title.trim();
  if (title.length === 0) errors.title = 'Title wajib diisi';
  else if (title.length > TITLE_MAX) errors.title = `Title maksimal ${TITLE_MAX} karakter`;

  if (draft.description.length > TEXT_MAX) errors.description = `Description maksimal ${TEXT_MAX} karakter`;
  if (draft.objective.length > TEXT_MAX) errors.objective = `Objective maksimal ${TEXT_MAX} karakter`;
  if (draft.precondition.length > TEXT_MAX) errors.precondition = `Pre-condition maksimal ${TEXT_MAX} karakter`;

  const { testStep, expectedResult } = serializeSteps(draft.steps);
  if ((testStep?.length ?? 0) > TEXT_MAX || (expectedResult?.length ?? 0) > TEXT_MAX) {
    errors.steps = `Langkah terlalu panjang (maksimal ${TEXT_MAX} karakter)`;
  }
  return errors;
}

export function isDraftValid(draft: EditableDraft): boolean {
  return Object.keys(validateDraft(draft)).length === 0;
}

/** Yang ditampilkan di kartu: kesalahan lokal didahulukan, lalu pesan dari server utk kolom lain. */
export function displayErrors(draft: EditableDraft): DraftErrors {
  return { ...draft.serverErrors, ...validateDraft(draft) };
}

// ---------- Pemilihan ----------

export function selectedDrafts(drafts: EditableDraft[]): EditableDraft[] {
  return drafts.filter((draft) => draft.selected);
}

export function selectionState(drafts: EditableDraft[]): 'all' | 'some' | 'none' {
  const count = selectedDrafts(drafts).length;
  if (count === 0) return 'none';
  return count === drafts.length ? 'all' : 'some';
}

export function setAllSelected(drafts: EditableDraft[], selected: boolean): EditableDraft[] {
  return drafts.map((draft) => ({ ...draft, selected }));
}

export function removeDraft(drafts: EditableDraft[], tempId: string): EditableDraft[] {
  return drafts.filter((draft) => draft.tempId !== tempId);
}

/** Tombol Simpan: butuh minimal satu draft tercentang dan semua yang tercentang valid. */
export function canSave(drafts: EditableDraft[]): { ok: boolean; reason: string | null } {
  const selected = selectedDrafts(drafts);
  if (selected.length === 0) return { ok: false, reason: 'Pilih minimal satu draft untuk disimpan.' };
  const invalid = selected.filter((draft) => !isDraftValid(draft)).length;
  if (invalid > 0) return { ok: false, reason: `Perbaiki ${invalid} draft yang belum valid.` };
  return { ok: true, reason: null };
}

export function saveButtonLabel(drafts: EditableDraft[], folderName: string): string {
  return `Simpan ${selectedDrafts(drafts).length} test case ke folder ${folderName}`;
}

// ---------- Ke payload commit ----------

export function toCommitItem(draft: EditableDraft): CommitTestCaseItem {
  const { testStep, expectedResult } = serializeSteps(draft.steps);
  return {
    title: draft.title.trim(),
    priority: draft.priority,
    type: draft.type,
    scenarioType: draft.scenarioType,
    description: draft.description.trim() || null,
    objective: draft.objective.trim() || null,
    precondition: draft.precondition.trim() || null,
    testStep,
    expectedResult,
  };
}

/** Hanya draft tercentang, dalam urutan tampil. Posisi pada daftar ini = `index` pada error 400 INVALID_DRAFTS. */
export function toCommitItems(drafts: EditableDraft[]): CommitTestCaseItem[] {
  return selectedDrafts(drafts).map(toCommitItem);
}

// ---------- Deteksi editan ----------

function snapshot(fields: {
  title: string;
  priority: string;
  type: string;
  scenarioType: string | null;
  description: string | null;
  objective: string | null;
  precondition: string | null;
  steps: { action: string; expected: string }[];
}): string {
  return JSON.stringify([
    fields.title.trim(),
    fields.priority,
    fields.type,
    fields.scenarioType,
    (fields.description ?? '').trim(),
    (fields.objective ?? '').trim(),
    (fields.precondition ?? '').trim(),
    fields.steps
      .map((s) => ({ action: s.action.trim(), expected: s.expected.trim() }))
      .filter((s) => s.action !== '' || s.expected !== ''),
  ]);
}

/** true = isi draft berbeda dari hasil asli AI (spasi pinggir, null vs kosong, dan baris langkah kosong tidak dianggap beda). */
export function isDraftEdited(draft: EditableDraft, original: TestCaseDraft): boolean {
  return (
    snapshot({
      ...draft,
      steps: draft.steps.map((s) => ({ action: s.action, expected: s.expectedResult })),
    }) !== snapshot(original)
  );
}

/** Ada editan yang akan hilang kalau dibuang: isi diubah atau ada draft yang dihapus (centang tidak dihitung). */
export function hasEdits(drafts: EditableDraft[], originals: TestCaseDraft[]): boolean {
  if (drafts.length !== originals.length) return true;
  const byId = new Map(originals.map((original) => [original.tempId, original]));
  return drafts.some((draft) => {
    const original = byId.get(draft.tempId);
    return original === undefined || isDraftEdited(draft, original);
  });
}

// ---------- Error dari server ----------

/** Nama kolom di backend -> kolom di kartu. testStep & expectedResult sama-sama tampil di bagian langkah. */
export function serverFieldKey(field: string): DraftFieldKey {
  switch (field) {
    case 'priority':
    case 'type':
    case 'description':
    case 'objective':
    case 'precondition':
      return field;
    case 'testStep':
    case 'expectedResult':
      return 'steps';
    default:
      return 'title';
  }
}

/**
 * Menempelkan error 400 INVALID_DRAFTS ke kartu yang bersangkutan. `submittedTempIds` = tempId draft yang DIKIRIM, berurutan;
 * `index` pada error adalah posisi pada daftar kiriman itu. Kartu yang bermasalah otomatis dibuka. Error lama dihapus.
 */
export function applyCommitErrors(drafts: EditableDraft[], submittedTempIds: string[], errors: CommitItemError[]): EditableDraft[] {
  const byTempId = new Map<string, DraftErrors>();
  for (const error of errors) {
    const tempId = submittedTempIds[error.index];
    if (tempId === undefined) continue;
    const current = byTempId.get(tempId) ?? {};
    const key = serverFieldKey(error.field);
    if (current[key] === undefined) current[key] = error.message;
    byTempId.set(tempId, current);
  }
  return drafts.map((draft) => {
    const serverErrors = byTempId.get(draft.tempId) ?? {};
    return { ...draft, serverErrors, expanded: draft.expanded || Object.keys(serverErrors).length > 0 };
  });
}

export function countDraftsWithServerErrors(drafts: EditableDraft[]): number {
  return drafts.filter((draft) => Object.keys(draft.serverErrors).length > 0).length;
}

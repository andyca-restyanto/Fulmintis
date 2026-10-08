// frontend/src/modules/automation/utils/automationOptions.ts
// Logika murni (tanpa Vue/axios) utk form setup. Validitas kombinasi framework x bahasa SELALU dibaca dari
// matriks `compatibility` hasil GET /options -- tidak ada aturan "Cypress tidak bisa Java" yang ditulis di sini.

import type {
  AutomationFramework,
  AutomationLanguage,
  AutomationOptions,
  AutomationPattern,
  AutomationSetup,
} from '../types/automation.types';

// ---- Teks tampilan (BUKAN aturan validitas) ----
const FRAMEWORK_LABELS: Record<string, string> = {
  PLAYWRIGHT: 'Playwright',
  CYPRESS: 'Cypress',
  SELENIUM: 'Selenium',
};

const LANGUAGE_LABELS: Record<string, string> = {
  JAVA: 'Java',
  JAVASCRIPT: 'JavaScript',
  TYPESCRIPT: 'TypeScript',
  PYTHON: 'Python',
};

const PATTERN_LABELS: Record<string, string> = {
  PAGE_OBJECT_MODEL: 'Page Object Model',
  SIMPLE: 'Simple',
};

const PATTERN_DESCRIPTIONS: Record<string, string> = {
  PAGE_OBJECT_MODEL: 'Locator dan aksi halaman dipisah ke kelas page; test hanya memanggil page dan melakukan assertion.',
  SIMPLE: 'Setiap test berdiri sendiri dalam satu berkas, tanpa page object.',
};

export function frameworkLabel(framework: string | null | undefined): string {
  return framework ? (FRAMEWORK_LABELS[framework] ?? framework) : '-';
}

export function languageLabel(language: string | null | undefined): string {
  return language ? (LANGUAGE_LABELS[language] ?? language) : '-';
}

export function patternLabel(pattern: string | null | undefined): string {
  return pattern ? (PATTERN_LABELS[pattern] ?? pattern) : '-';
}

export function patternDescription(pattern: string): string {
  return PATTERN_DESCRIPTIONS[pattern] ?? '';
}

// ---- Matriks kompatibilitas (dari backend) ----

export function languagesFor(options: AutomationOptions, framework: AutomationFramework | null): AutomationLanguage[] {
  return framework ? (options.compatibility[framework] ?? []) : [];
}

export function isLanguageSupported(
  options: AutomationOptions,
  framework: AutomationFramework | null,
  language: AutomationLanguage | null
): boolean {
  return framework !== null && language !== null && languagesFor(options, framework).includes(language);
}

/** Alasan utk tooltip pada pilihan yang dinonaktifkan; sama dengan pesan penolakan backend ("Cypress tidak mendukung Java."). */
export function unsupportedReason(framework: AutomationFramework, language: AutomationLanguage): string {
  return `${frameworkLabel(framework)} tidak mendukung ${languageLabel(language)}.`;
}

/**
 * Saat framework diganti: bahasa terpilih dipertahankan kalau masih valid; kalau tidak, pindah ke bahasa valid
 * pertama (urutan dari backend), atau null kalau framework tidak punya bahasa valid.
 */
export function languageAfterFrameworkChange(
  options: AutomationOptions,
  newFramework: AutomationFramework,
  currentLanguage: AutomationLanguage | null
): AutomationLanguage | null {
  if (currentLanguage !== null && isLanguageSupported(options, newFramework, currentLanguage)) {
    return currentLanguage;
  }
  return languagesFor(options, newFramework)[0] ?? null;
}

// ---- Form ----

export const STRUCTURE_NOTES_MAX = 4000;

export interface SetupForm {
  framework: AutomationFramework | null;
  language: AutomationLanguage | null;
  pattern: AutomationPattern | null;
  structureNotes: string;
}

export function emptyForm(options: AutomationOptions): SetupForm {
  return {
    framework: null,
    language: null,
    pattern: options.patterns.includes('PAGE_OBJECT_MODEL' as AutomationPattern)
      ? ('PAGE_OBJECT_MODEL' as AutomationPattern)
      : (options.patterns[0] ?? null),
    structureNotes: '',
  };
}

export function formFromSetup(setup: AutomationSetup, options: AutomationOptions): SetupForm {
  if (!setup.configured) {
    return emptyForm(options);
  }
  return {
    framework: setup.framework,
    language: setup.language,
    pattern: setup.pattern,
    structureNotes: setup.structureNotes ?? '',
  };
}

export function isFormValid(options: AutomationOptions, form: SetupForm): boolean {
  return (
    form.framework !== null &&
    form.language !== null &&
    form.pattern !== null &&
    isLanguageSupported(options, form.framework, form.language) &&
    form.structureNotes.length <= STRUCTURE_NOTES_MAX
  );
}

/** Ada perubahan dibanding yang tersimpan? Setup belum ada = selalu dianggap berubah. */
export function isFormDirty(form: SetupForm, setup: AutomationSetup): boolean {
  if (!setup.configured) {
    return true;
  }
  return (
    form.framework !== setup.framework ||
    form.language !== setup.language ||
    form.pattern !== setup.pattern ||
    form.structureNotes.trim() !== (setup.structureNotes ?? '').trim()
  );
}

/** Body PUT /setup; structureNotes kosong tidak dikirim. */
export function toSaveRequest(form: SetupForm): {
  framework: AutomationFramework;
  language: AutomationLanguage;
  pattern: AutomationPattern;
  structureNotes?: string;
} {
  if (form.framework === null || form.language === null || form.pattern === null) {
    throw new Error('Form setup belum lengkap');
  }
  const notes = form.structureNotes.trim();
  return {
    framework: form.framework,
    language: form.language,
    pattern: form.pattern,
    ...(notes.length > 0 ? { structureNotes: notes } : {}),
  };
}

/** "Playwright · TypeScript · Page Object Model" */
export function setupSummary(setup: Pick<AutomationSetup, 'framework' | 'language' | 'pattern'>): string {
  return [frameworkLabel(setup.framework), languageLabel(setup.language), patternLabel(setup.pattern)].join(' · ');
}

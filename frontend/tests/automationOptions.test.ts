// filepath: /frontend/tests/automationOptions.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  emptyForm, formFromSetup, frameworkLabel, frameworkNote, isFormDirty, isFormValid, isLanguageSupported,
  languageAfterFrameworkChange, languagesFor, patternDescription, setupSummary, structurePlaceholder, toSaveRequest,
  unsupportedReason, type SetupForm,
} from '../src/modules/automation/utils/automationOptions.ts';
import { zipFileName } from '../src/modules/automation/utils/automationFiles.ts';
import type { AutomationOptions, AutomationSetup } from '../src/modules/automation/types/automation.types.ts';

// Persis bentuk GET /options dari backend (CHANGES bagian 35): Robot Framework terakhir, hanya Python.
const options: AutomationOptions = {
  frameworks: ['PLAYWRIGHT', 'CYPRESS', 'SELENIUM', 'ROBOT_FRAMEWORK'],
  languages: ['JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'PYTHON'],
  compatibility: {
    PLAYWRIGHT: ['JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'PYTHON'],
    CYPRESS: ['JAVASCRIPT', 'TYPESCRIPT'],
    SELENIUM: ['JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'PYTHON'],
    ROBOT_FRAMEWORK: ['PYTHON'],
  },
  patterns: ['PAGE_OBJECT_MODEL', 'SIMPLE'],
};

const unconfigured: AutomationSetup = {
  configured: false, framework: null, language: null, pattern: null, structureNotes: null, updatedAt: null, updatedBy: null,
};

test('label Robot Framework dan framework lama; kode tidak dikenal ditampilkan apa adanya', () => {
  assert.equal(frameworkLabel('ROBOT_FRAMEWORK'), 'Robot Framework');
  assert.equal(frameworkLabel('SELENIUM'), 'Selenium');
  assert.equal(frameworkLabel('LAIN'), 'LAIN');
  assert.equal(frameworkLabel(null), '-');
});

test('Robot Framework hanya mendukung Python; validitas dibaca dari matriks backend', () => {
  assert.deepEqual(languagesFor(options, 'ROBOT_FRAMEWORK'), ['PYTHON']);
  assert.equal(isLanguageSupported(options, 'ROBOT_FRAMEWORK', 'PYTHON'), true);
  for (const language of ['JAVA', 'JAVASCRIPT', 'TYPESCRIPT'] as const) {
    assert.equal(isLanguageSupported(options, 'ROBOT_FRAMEWORK', language), false, language);
  }
  assert.equal(unsupportedReason('ROBOT_FRAMEWORK', 'JAVA'), 'Robot Framework tidak mendukung Java.');
  // framework lama tidak berubah
  assert.equal(isLanguageSupported(options, 'SELENIUM', 'JAVA'), true);
  assert.equal(isLanguageSupported(options, 'CYPRESS', 'PYTHON'), false);
});

test('pindah ke Robot Framework otomatis memilih Python; pindah kembali mempertahankan bahasa yang masih valid', () => {
  assert.equal(languageAfterFrameworkChange(options, 'ROBOT_FRAMEWORK', 'JAVA'), 'PYTHON');
  assert.equal(languageAfterFrameworkChange(options, 'ROBOT_FRAMEWORK', 'TYPESCRIPT'), 'PYTHON');
  assert.equal(languageAfterFrameworkChange(options, 'ROBOT_FRAMEWORK', null), 'PYTHON');
  assert.equal(languageAfterFrameworkChange(options, 'ROBOT_FRAMEWORK', 'PYTHON'), 'PYTHON');
  // dari Robot (Python) ke framework yang juga mendukung Python: dipertahankan
  assert.equal(languageAfterFrameworkChange(options, 'PLAYWRIGHT', 'PYTHON'), 'PYTHON');
  assert.equal(languageAfterFrameworkChange(options, 'SELENIUM', 'PYTHON'), 'PYTHON');
  // dari Robot ke Cypress: Python tidak valid -> bahasa valid pertama Cypress
  assert.equal(languageAfterFrameworkChange(options, 'CYPRESS', 'PYTHON'), 'JAVASCRIPT');
});

test('form Robot Framework + Python valid; Robot + Java tidak valid', () => {
  const form: SetupForm = { framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', pattern: 'PAGE_OBJECT_MODEL', structureNotes: '' };
  assert.equal(isFormValid(options, form), true);
  assert.equal(isFormValid(options, { ...form, language: 'JAVA' }), false);
  assert.deepEqual(toSaveRequest(form), { framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', pattern: 'PAGE_OBJECT_MODEL' });
  assert.deepEqual(
    toSaveRequest({ ...form, structureNotes: '  Pakai Browser library  ' }),
    { framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', pattern: 'PAGE_OBJECT_MODEL', structureNotes: 'Pakai Browser library' }
  );
});

test('setup Robot tersimpan: form, dirty, dan ringkasan', () => {
  const saved: AutomationSetup = {
    configured: true, framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', pattern: 'SIMPLE', structureNotes: null,
    updatedAt: '2026-10-18T10:00:00', updatedBy: 'a@x.id',
  };
  const form = formFromSetup(saved, options);
  assert.deepEqual(form, { framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', pattern: 'SIMPLE', structureNotes: '' });
  assert.equal(isFormDirty(form, saved), false);
  assert.equal(isFormDirty({ ...form, framework: 'SELENIUM' }, saved), true);
  assert.equal(setupSummary(saved), 'Robot Framework · Python · Simple');
  assert.deepEqual(emptyForm(options), { framework: null, language: null, pattern: 'PAGE_OBJECT_MODEL', structureNotes: '' });
  assert.equal(isFormDirty(emptyForm(options), unconfigured), true);
});

test('deskripsi pola berbeda untuk Robot (resource file), framework lain tetap memakai kelas page', () => {
  const generic = patternDescription('PAGE_OBJECT_MODEL');
  assert.match(generic, /kelas page/);
  assert.equal(patternDescription('PAGE_OBJECT_MODEL', 'PLAYWRIGHT'), generic);
  assert.equal(patternDescription('PAGE_OBJECT_MODEL', null), generic);
  const robotPom = patternDescription('PAGE_OBJECT_MODEL', 'ROBOT_FRAMEWORK');
  assert.match(robotPom, /resource/);
  assert.doesNotMatch(robotPom, /kelas page/);
  assert.match(patternDescription('SIMPLE', 'ROBOT_FRAMEWORK'), /\.robot/);
  assert.equal(patternDescription('SIMPLE', 'CYPRESS'), patternDescription('SIMPLE'));
  assert.equal(patternDescription('TIDAK_ADA', 'ROBOT_FRAMEWORK'), '');
});

test('catatan framework dan contoh struktur hanya khusus Robot', () => {
  assert.match(frameworkNote('ROBOT_FRAMEWORK') ?? '', /Python.*SeleniumLibrary/);
  assert.equal(frameworkNote('SELENIUM'), null);
  assert.equal(frameworkNote(null), null);
  assert.match(structurePlaceholder('ROBOT_FRAMEWORK'), /\.resource/);
  assert.doesNotMatch(structurePlaceholder('ROBOT_FRAMEWORK'), /page object/);
  assert.match(structurePlaceholder('PLAYWRIGHT'), /page object/);
  assert.equal(structurePlaceholder(null), structurePlaceholder('CYPRESS'));
  assert.ok(structurePlaceholder('ROBOT_FRAMEWORK').includes('\n'), 'contoh multi-baris');
});

test('nama berkas zip Robot Framework wajar', () => {
  assert.equal(
    zipFileName({ framework: 'ROBOT_FRAMEWORK', language: 'PYTHON', createdAt: '2026-10-18T10:00:00' }),
    'automation-robot_framework-python-20261018.zip'
  );
});

test('tab setup memakai grid 4 kolom, catatan framework, deskripsi pola per framework, dan contoh struktur dinamis', () => {
  const source = readFileSync(new URL('../src/modules/automation/components/AutomationSetupTab.vue', import.meta.url), 'utf8');
  assert.match(source, /sm:grid-cols-2 lg:grid-cols-4" role="radiogroup" aria-label="Framework"/);
  assert.doesNotMatch(source, /sm:grid-cols-3" role="radiogroup" aria-label="Framework"/);
  assert.match(source, /frameworkNote\(form\.framework\)/);
  assert.match(source, /patternDescription\(pattern, form\.framework\)/);
  assert.match(source, /:placeholder="structurePlaceholder\(form\.framework\)"/);
});

test('tipe AutomationFramework memuat ROBOT_FRAMEWORK', () => {
  const source = readFileSync(new URL('../src/modules/automation/types/automation.types.ts', import.meta.url), 'utf8');
  assert.match(source, /export type AutomationFramework = 'PLAYWRIGHT' \| 'CYPRESS' \| 'SELENIUM' \| 'ROBOT_FRAMEWORK';/);
});

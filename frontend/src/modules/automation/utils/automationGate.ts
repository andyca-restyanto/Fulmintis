// frontend/src/modules/automation/utils/automationGate.ts
// Kapan tombol Generate boleh ditekan, dan teks pemakaian harian. Murni -- backend tetap penjaga akhir.

import type { AutomationUsage } from '../types/automation.types';

export interface GenerateGateInput {
  setupConfigured: boolean;
  aiEnabled: boolean;
  selectedCount: number;
  maxPerGeneration: number;
  /** 0 = tanpa batas. */
  dailyLimit: number;
  usedToday: number;
  /** Ada generate milik user yang sedang berjalan / sedang mengirim. */
  isBusy: boolean;
}

/** Alasan tombol dinonaktifkan, atau null kalau boleh. Urutan = prioritas pesan yang paling berguna. */
export function generateBlockReason(input: GenerateGateInput): string | null {
  if (!input.setupConfigured) {
    return 'Atur struktur automation terlebih dahulu di tab Setup.';
  }
  if (!input.aiEnabled) {
    return 'Fitur generate belum diaktifkan.';
  }
  if (input.isBusy) {
    return 'Generate sebelumnya masih diproses.';
  }
  if (input.selectedCount === 0) {
    return 'Pilih minimal satu test case.';
  }
  if (input.selectedCount > input.maxPerGeneration) {
    return `Maksimal ${input.maxPerGeneration} test case per generate.`;
  }
  if (input.dailyLimit > 0 && input.usedToday >= input.dailyLimit) {
    return `Batas generate harian tercapai (${input.usedToday}/${input.dailyLimit}). Coba lagi besok.`;
  }
  return null;
}

export function tierLabel(tier: string): string {
  return tier === 'VIP' ? 'VIP' : 'Free';
}

/** "Hari ini 1 dari 3 generate" atau "Hari ini 1 generate (tanpa batas harian)". */
export function usageText(usage: Pick<AutomationUsage, 'dailyLimit' | 'usedToday'>): string {
  return usage.dailyLimit > 0
    ? `Hari ini ${usage.usedToday} dari ${usage.dailyLimit} generate`
    : `Hari ini ${usage.usedToday} generate (tanpa batas harian)`;
}

/** Sisa jatah harian, atau null kalau tanpa batas. */
export function remainingToday(usage: Pick<AutomationUsage, 'dailyLimit' | 'usedToday'>): number | null {
  return usage.dailyLimit > 0 ? Math.max(0, usage.dailyLimit - usage.usedToday) : null;
}

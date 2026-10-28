// filepath: /frontend/src/modules/admin/types/admin-ai-limits.types.ts

// Padanan DTO backend modules/admin/dto: AiLimitsDTO (+ nested), AiLimitsUpdateRequestDTO.

/** Tier yang punya batas AI sendiri. VIP Monthly dan VIP Yearly dibedakan. */
export type AiLimitTier = 'FREE' | 'VIP_MONTHLY' | 'VIP_YEARLY';

export interface AiGlobalLimits {
  /** Maks. generate seluruh sistem per hari, semua fitur AI. 0 = tanpa batas. */
  dailyLimit: number;
  /** Menit panggilan ke provider ditahan setelah provider melaporkan kuota/saldo habis. */
  quotaCircuitMinutes: number;
}

export interface AiAutomationLimits {
  /** Generate per user per hari. 0 = tanpa batas. */
  dailyLimit: number;
  maxTestCases: number;
  maxOutputTokens: number;
  timeoutSeconds: number;
}

export interface AiTestcaseLimits {
  /** Generate per user per hari. 0 = tanpa batas. */
  dailyLimit: number;
  maxDrafts: number;
  maxOutputTokens: number;
  timeoutSeconds: number;
}

export interface AiTierLimits {
  automation: AiAutomationLimits;
  testcase: AiTestcaseLimits;
}

export type AiLimitSource = 'DATABASE' | 'DEFAULT';

export interface AiLimitsDefaults {
  global: AiGlobalLimits;
  tiers: Record<AiLimitTier, AiTierLimits>;
}

/** Respons GET/PUT/DELETE /admin/ai-limits. Nilai = batas EFEKTIF; source per kunci "FREE.AUTOMATION_DAILY_LIMIT". */
export interface AiLimits {
  global: AiGlobalLimits;
  tiers: Record<AiLimitTier, AiTierLimits>;
  source: Record<string, AiLimitSource>;
  defaults: AiLimitsDefaults;
}

/** Body PUT /admin/ai-limits: SEMUA nilai. confirmUnlimited wajib true bila ada batas harian diubah menjadi 0 (tanpa batas). */
export interface AiLimitsUpdateRequest {
  global: AiGlobalLimits;
  tiers: Record<AiLimitTier, AiTierLimits>;
  confirmUnlimited: boolean;
}

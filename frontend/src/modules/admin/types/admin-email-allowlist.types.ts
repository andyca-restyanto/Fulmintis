// filepath: /frontend/src/modules/admin/types/admin-email-allowlist.types.ts
// Match 100%: backend modules/emailallowlist/dto/*

export type AllowedEmailRuleType = 'EMAIL' | 'DOMAIN';

/** AllowedEmailRuleDTO */
export interface AllowedEmailRule {
  id: number;
  ruleType: AllowedEmailRuleType;
  /** Huruf kecil. EMAIL = alamat lengkap, DOMAIN = "@perusahaan.com". */
  value: string;
  /** LocalDateTime ISO tanpa zona, mis. "2026-10-28T13:00:00". */
  createdAt: string;
  createdBy: string;
}

/** AllowlistResponseDTO */
export interface AllowlistResponse {
  enabled: boolean;
  rules: AllowedEmailRule[];
}

/** AddAllowedEmailRequestDTO */
export interface AddAllowedEmailRequest {
  ruleType: AllowedEmailRuleType;
  value: string;
}

/** AllowlistSettingsRequestDTO */
export interface AllowlistSettingsRequest {
  enabled: boolean;
}

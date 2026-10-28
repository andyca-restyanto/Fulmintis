// frontend/src/modules/auth/composables/usePasswordValidation.ts
import { computed, type Ref } from 'vue';

export interface PasswordRule {
  key: string;
  label: string;
  test: (value: string) => boolean;
}

// Aturan HARUS identik dengan regex backend:
// ^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@#$%^&+=!()\-_.,?*]).{9,}$
export const passwordRules: PasswordRule[] = [
  { key: 'minLength', label: 'Minimal 9 karakter', test: (v) => v.length >= 9 },
  { key: 'uppercase', label: '1 huruf besar', test: (v) => /[A-Z]/.test(v) },
  { key: 'lowercase', label: '1 huruf kecil', test: (v) => /[a-z]/.test(v) },
  { key: 'number', label: '1 angka', test: (v) => /\d/.test(v) },
  {
    key: 'special',
    label: '1 karakter spesial',
    test: (v) => /[@#$%^&+=!()\-_.,?*]/.test(v),
  },
];

export function usePasswordValidation(password: Ref<string>) {
  const ruleResults = computed(() =>
    passwordRules.map((rule) => ({
      ...rule,
      passed: rule.test(password.value),
    }))
  );

  const isPasswordValid = computed(() =>
    ruleResults.value.every((r) => r.passed)
  );

  return { ruleResults, isPasswordValid };
}

<!-- frontend/src/modules/auth/components/VerificationNotice.vue -->
<script setup lang="ts">
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';
import XIcon from '@/shared/components/icons/XIcon.vue';
import ResendVerificationForm from './ResendVerificationForm.vue';

const props = withDefaults(
  defineProps<{
    status: 'success' | 'error' | null;
    reason?: string | null;
    /** Override pesan default (dipakai ulang utk notifikasi selain verifikasi email, misal reset password). */
    successMessage?: string;
    errorMessage?: string;
    /** Tampilkan form "kirim ulang link verifikasi" di state error (hanya utk error verifikasi email). */
    allowResend?: boolean;
    /** Email awal utk form kirim ulang. */
    resendEmail?: string;
  }>(),
  {
    successMessage: 'Account Verification Success! Silakan sign in.',
    allowResend: false,
    resendEmail: '',
  }
);

const emit = defineEmits<{ (e: 'dismiss'): void }>();

function resolvedErrorMessage(): string {
  if (props.errorMessage) return props.errorMessage;
  if (props.reason === 'expired') {
    return 'Link verifikasi sudah kedaluwarsa. Kirim ulang link baru lewat form di bawah.';
  }
  return props.allowResend
    ? 'Link verifikasi tidak valid. Kamu bisa meminta link baru lewat form di bawah.'
    : 'Link verifikasi tidak valid.';
}
</script>

<template>
  <div
    v-if="status === 'success'"
    class="mb-6 flex items-start gap-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3.5 text-sm text-green-700"
  >
    <CheckCircleIcon :size="20" class="mt-0.5 shrink-0" />
    <span class="flex-1 font-medium">{{ successMessage }}</span>
    <button type="button" class="shrink-0 text-green-500 hover:text-green-700" @click="emit('dismiss')">
      <XIcon :size="16" />
    </button>
  </div>

  <div
    v-else-if="status === 'error'"
    class="mb-6 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3.5 text-sm text-red-700"
  >
    <AlertCircleIcon :size="20" class="mt-0.5 shrink-0" />
    <div class="flex-1 space-y-3">
      <span class="block font-medium">{{ resolvedErrorMessage() }}</span>
      <ResendVerificationForm v-if="allowResend" :initial-email="resendEmail" />
    </div>
    <button type="button" class="shrink-0 text-red-500 hover:text-red-700" @click="emit('dismiss')">
      <XIcon :size="16" />
    </button>
  </div>
</template>

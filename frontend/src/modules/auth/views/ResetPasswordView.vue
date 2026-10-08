<!-- frontend/src/modules/auth/views/ResetPasswordView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import { authService } from '../services/auth.service';
import type { ApiValidationErrorResponse } from '../types/register.types';
import { usePasswordValidation } from '../composables/usePasswordValidation';
import PasswordChecklist from '../components/PasswordChecklist.vue';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import { BRAND_NAME } from '@/shared/config/brand';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import EyeOffIcon from '@/shared/components/icons/EyeOffIcon.vue';

const route = useRoute();
const router = useRouter();

const token = typeof route.query.token === 'string' ? route.query.token : '';

// 'checking' -> lagi validasi token ke backend
// 'valid'    -> token oke, tampilkan form
// 'invalid'  -> token invalid/expired/tidak ada, tampilkan pesan error
const tokenState = ref<'checking' | 'valid' | 'invalid'>('checking');

const newPassword = ref('');
const confirmNewPassword = ref('');
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const isSubmitting = ref(false);
const generalError = ref('');
const serverErrors = ref<Record<string, string>>({});

const passwordRef = computed(() => newPassword.value);
const { isPasswordValid } = usePasswordValidation(passwordRef);

const passwordsMatch = computed(
  () => confirmNewPassword.value.length > 0 && newPassword.value === confirmNewPassword.value
);

const isFormValid = computed(() => isPasswordValid.value && passwordsMatch.value);

onMounted(async () => {
  if (!token) {
    tokenState.value = 'invalid';
    return;
  }

  try {
    await authService.validateResetToken(token);
    tokenState.value = 'valid';
  } catch {
    tokenState.value = 'invalid';
  }
});

function clearFieldError(field: string) {
  if (serverErrors.value[field]) {
    delete serverErrors.value[field];
  }
}

async function handleSubmit() {
  generalError.value = '';
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    await authService.resetPassword({
      token,
      newPassword: newPassword.value,
      confirmNewPassword: confirmNewPassword.value,
    });
    // Redirect ke Sign In dengan notifikasi sukses (AuthView baca query "reset")
    router.push({ name: 'auth-signin', query: { reset: 'true' } });
  } catch (err) {
    if (axios.isAxiosError<ApiValidationErrorResponse>(err)) {
      const data = err.response?.data;
      if (err.response?.status === 400 && data?.errors) {
        serverErrors.value = data.errors;
      } else if (err.response?.status === 400) {
        // Token jadi invalid di antara waktu validasi awal & submit (misal
        // dipakai di tab lain / expired pas lagi ngisi form)
        tokenState.value = 'invalid';
      } else {
        generalError.value = data?.message ?? 'Terjadi kesalahan, coba lagi';
      }
    } else {
      generalError.value = 'Tidak dapat terhubung ke server';
    }
  } finally {
    isSubmitting.value = false;
  }
}

function goToForgotPassword() {
  router.push({ name: 'auth-forgot-password' });
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4 py-10">
    <div class="w-full max-w-md rounded-3xl border border-gray-200 bg-white p-10 shadow-sm">
      <!-- Header -->
      <div class="mb-8 text-center">
        <h1 class="flex items-center justify-center gap-2 text-3xl font-bold text-gray-900">
          <BugIcon :size="28" />
          {{ BRAND_NAME }}
        </h1>
        <p class="mt-2 text-base text-gray-400">Reset your password</p>
      </div>

      <!-- Checking token -->
      <p v-if="tokenState === 'checking'" class="text-center text-sm text-gray-400">
        Memeriksa link reset password...
      </p>

      <!-- Invalid/expired token -->
      <div v-else-if="tokenState === 'invalid'" class="text-center">
        <div class="mb-4 flex justify-center text-red-500">
          <AlertCircleIcon :size="48" />
        </div>
        <p class="text-base font-medium text-gray-900">Link tidak valid</p>
        <p class="mt-2 text-sm text-gray-500">
          Link reset password ini tidak valid atau sudah kedaluwarsa. Silakan request link baru.
        </p>
        <button
          type="button"
          class="mt-8 w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
          @click="goToForgotPassword"
        >
          Request Link Baru
        </button>
      </div>

      <!-- Valid token -> form -->
      <form v-else class="space-y-6" @submit.prevent="handleSubmit">
        <!-- New Password -->
        <div>
          <label for="new-password" class="mb-2 block text-base font-semibold text-gray-900">New Password</label>
          <div class="relative">
            <input
              id="new-password"
              v-model="newPassword"
              :type="showPassword ? 'text' : 'password'"
              placeholder="••••••••"
              class="w-full rounded-xl border border-gray-200 px-4 py-3.5 pr-12 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': serverErrors.newPassword }"
              @input="clearFieldError('newPassword')"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-4 flex items-center text-gray-400 hover:text-gray-600"
              @click="showPassword = !showPassword"
              tabindex="-1"
            >
              <EyeOffIcon v-if="showPassword" :size="20" />
              <EyeIcon v-else :size="20" />
            </button>
          </div>
          <PasswordChecklist :password="newPassword" />
          <p v-if="serverErrors.newPassword" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.newPassword }}
          </p>
        </div>

        <!-- Confirm New Password -->
        <div>
          <label for="confirm-new-password" class="mb-2 block text-base font-semibold text-gray-900">
            Confirm New Password
          </label>
          <div class="relative">
            <input
              id="confirm-new-password"
              v-model="confirmNewPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="••••••••"
              class="w-full rounded-xl border border-gray-200 px-4 py-3.5 pr-12 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': serverErrors.confirmNewPassword }"
              @input="clearFieldError('confirmNewPassword')"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-4 flex items-center text-gray-400 hover:text-gray-600"
              @click="showConfirmPassword = !showConfirmPassword"
              tabindex="-1"
            >
              <EyeOffIcon v-if="showConfirmPassword" :size="20" />
              <EyeIcon v-else :size="20" />
            </button>
          </div>
          <p
            v-if="confirmNewPassword && !passwordsMatch"
            class="mt-1.5 text-sm text-red-600"
          >
            Password dan konfirmasi tidak sama
          </p>
          <p v-if="serverErrors.confirmNewPassword" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.confirmNewPassword }}
          </p>
        </div>

        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

        <button
          type="submit"
          :disabled="!isFormValid || isSubmitting"
          class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Saving...' : 'Reset Password' }}
        </button>
      </form>
    </div>
  </div>
</template>

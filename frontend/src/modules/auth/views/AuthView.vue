<!-- frontend/src/modules/auth/views/AuthView.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { authService } from '../services/auth.service';
import type { RegisterRequest, ApiValidationErrorResponse } from '../types/register.types';
import { usePasswordValidation } from '../composables/usePasswordValidation';
import PasswordChecklist from '../components/PasswordChecklist.vue';
import AuthTabs from '../components/AuthTabs.vue';
import LoginForm from '../components/LoginForm.vue';
import VerificationNotice from '../components/VerificationNotice.vue';
import ResendVerificationForm from '../components/ResendVerificationForm.vue';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import { BRAND_NAME } from '@/shared/config/brand';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import EyeOffIcon from '@/shared/components/icons/EyeOffIcon.vue';
import axios from 'axios';

const route = useRoute();
const router = useRouter();

// Kalau user datang dari link "/auth/signin" (misal hasil redirect verifikasi
// email), tab Sign In otomatis aktif. Selain itu default ke Sign Up.
const activeTab = ref<'signin' | 'signup'>(route.name === 'auth-signin' ? 'signin' : 'signup');

const subtitle = computed(() =>
  activeTab.value === 'signin' ? 'Welcome back' : 'Create your account'
);

// ---- Notifikasi hasil verifikasi email ATAU reset password sukses ----
// Backend redirect verifikasi: /auth/signin?verified=true|false&reason=...
// Frontend redirect setelah reset password sukses: /auth/signin?reset=true
// Frontend redirect saat sesi berakhir (apiClient, respons 401): /auth/signin?expired=true
const isSessionExpired = route.query.expired === 'true';
const verificationStatus = ref<'success' | 'error' | null>(
  route.query.verified === 'true' || route.query.reset === 'true'
    ? 'success'
    : route.query.verified === 'false' || isSessionExpired
      ? 'error'
      : null
);
// Error verifikasi email (link expired/invalid) menampilkan form kirim ulang;
// sesi berakhir cukup pesan biasa.
const allowResend = route.query.verified === 'false';
const noticeErrorMessage = isSessionExpired
  ? 'Sesi kamu telah berakhir. Silakan sign in lagi.'
  : undefined;
const verificationReason = ref<string | null>(
  typeof route.query.reason === 'string' ? route.query.reason : null
);
const noticeSuccessMessage = computed(() =>
  route.query.reset === 'true'
    ? 'Password berhasil diubah! Silakan sign in dengan password baru kamu.'
    : 'Account Verification Success! Silakan sign in.'
);

function dismissVerificationNotice() {
  verificationStatus.value = null;
  // Bersihkan query param dari URL supaya notifikasi tidak muncul lagi kalau di-refresh
  router.replace({ query: {} });
}

const form = ref<RegisterRequest>({
  email: '',
  password: '',
  confirmPassword: '',
});

const showPassword = ref(false);
const showConfirmPassword = ref(false);
const isSubmitting = ref(false);
const serverErrors = ref<Record<string, string>>({});
const generalError = ref('');
const successMessage = ref('');
// Email yang barusan didaftarkan -- dipakai form "kirim ulang verifikasi" di
// bawah pesan sukses (form.value dikosongkan setelah register berhasil).
const registeredEmail = ref('');

const passwordRef = computed(() => form.value.password);
const { isPasswordValid } = usePasswordValidation(passwordRef);

const passwordsMatch = computed(
  () =>
    form.value.confirmPassword.length > 0 &&
    form.value.password === form.value.confirmPassword
);

const isFormValid = computed(
  () =>
    form.value.email.trim().length > 0 &&
    isPasswordValid.value &&
    passwordsMatch.value
);

function clearFieldError(field: string) {
  if (serverErrors.value[field]) {
    delete serverErrors.value[field];
  }
}

async function handleSubmit() {
  generalError.value = '';
  successMessage.value = '';
  registeredEmail.value = '';
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    const response = await authService.register(form.value);
    // Pesan sukses diambil langsung dari backend (RegisterResponseDTO.message)
    successMessage.value = response.message;
    registeredEmail.value = form.value.email.trim();
    form.value = { email: '', password: '', confirmPassword: '' };
  } catch (err) {
    if (axios.isAxiosError<ApiValidationErrorResponse>(err)) {
      const data = err.response?.data;
      if (err.response?.status === 400 && data?.errors) {
        serverErrors.value = data.errors;
      } else if (err.response?.status === 409) {
        generalError.value = data?.message ?? 'Email sudah terdaftar';
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
        <p class="mt-2 text-base text-gray-400">{{ subtitle }}</p>
      </div>

      <!-- Notifikasi hasil verifikasi email / reset password (muncul di kedua tab) -->
      <VerificationNotice
        :status="verificationStatus"
        :reason="verificationReason"
        :success-message="noticeSuccessMessage"
        :error-message="noticeErrorMessage"
        :allow-resend="allowResend"
        @dismiss="dismissVerificationNotice"
      />

      <!-- Tabs -->
      <AuthTabs v-model="activeTab" class="mb-8" />

      <form v-if="activeTab === 'signup'" class="space-y-6" @submit.prevent="handleSubmit">
        <!-- Email -->
        <div>
          <label for="email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            placeholder="you@example.com"
            class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            :class="{ 'border-red-500': serverErrors.email }"
            @input="clearFieldError('email')"
          />
          <p v-if="serverErrors.email" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.email }}
          </p>
        </div>

        <!-- Password -->
        <div>
          <label for="password" class="mb-2 block text-base font-semibold text-gray-900">Password</label>
          <div class="relative">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="••••••••"
              class="w-full rounded-xl border border-gray-200 px-4 py-3.5 pr-12 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': serverErrors.password }"
              @input="clearFieldError('password')"
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
          <PasswordChecklist :password="form.password" />
          <p v-if="serverErrors.password" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.password }}
          </p>
        </div>

        <!-- Confirm Password -->
        <div>
          <label for="confirmPassword" class="mb-2 block text-base font-semibold text-gray-900">
            Confirm Password
          </label>
          <div class="relative">
            <input
              id="confirmPassword"
              v-model="form.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="••••••••"
              class="w-full rounded-xl border border-gray-200 px-4 py-3.5 pr-12 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': serverErrors.confirmPassword }"
              @input="clearFieldError('confirmPassword')"
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
            v-if="form.confirmPassword && !passwordsMatch"
            class="mt-1.5 text-sm text-red-600"
          >
            Password dan konfirmasi tidak sama
          </p>
          <p v-if="serverErrors.confirmPassword" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.confirmPassword }}
          </p>
        </div>

        <!-- General error / success -->
        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
        <p v-if="successMessage" class="text-sm text-green-600">{{ successMessage }}</p>
        <div v-if="successMessage && registeredEmail" class="rounded-xl bg-gray-50 p-4">
          <p class="mb-2 text-sm text-gray-600">Belum menerima email verifikasi?</p>
          <ResendVerificationForm :initial-email="registeredEmail" />
        </div>

        <!-- Submit -->
        <button
          type="submit"
          :disabled="!isFormValid || isSubmitting"
          class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Creating...' : 'Create Account' }}
        </button>
      </form>

      <LoginForm v-else />
    </div>
  </div>
</template>

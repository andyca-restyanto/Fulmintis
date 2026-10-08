<!-- frontend/src/modules/auth/components/LoginForm.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { authService } from '../services/auth.service';
import type { LoginRequest } from '../types/login.types';
import type { ApiValidationErrorResponse } from '../types/register.types';
import { tokenStorage } from '@/shared/services/tokenStorage';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import EyeOffIcon from '@/shared/components/icons/EyeOffIcon.vue';
import ResendVerificationForm from './ResendVerificationForm.vue';

const router = useRouter();

const form = ref<LoginRequest>({
  email: '',
  password: '',
});

const showPassword = ref(false);
const isSubmitting = ref(false);
const generalError = ref('');
const showResend = ref(false);
const serverErrors = ref<Record<string, string>>({});

const isFormValid = computed(
  () => form.value.email.trim().length > 0 && form.value.password.length > 0
);

function clearFieldError(field: string) {
  if (serverErrors.value[field]) {
    delete serverErrors.value[field];
  }
}

async function handleSubmit() {
  generalError.value = '';
  showResend.value = false;
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    const response = await authService.login(form.value);
    tokenStorage.setToken(response.accessToken);
    router.push({ name: 'dashboard' });
  } catch (err) {
    if (axios.isAxiosError<ApiValidationErrorResponse>(err)) {
      const data = err.response?.data;
      if (err.response?.status === 400 && data?.errors) {
        serverErrors.value = data.errors;
      } else if (err.response?.status === 401) {
        generalError.value = data?.message ?? 'Email atau password salah';
      } else if (err.response?.status === 403) {
        generalError.value =
          data?.message ?? 'Akun belum diverifikasi. Silakan cek email kamu.';
        showResend.value = true;
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
  <form class="space-y-6" @submit.prevent="handleSubmit">
    <!-- Email -->
    <div>
      <label for="login-email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
      <input
        id="login-email"
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
      <label for="login-password" class="mb-2 block text-base font-semibold text-gray-900">Password</label>
      <div class="relative">
        <input
          id="login-password"
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
      <p v-if="serverErrors.password" class="mt-1.5 text-sm text-red-600">
        {{ serverErrors.password }}
      </p>
    </div>

    <!-- General error -->
    <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
    <div v-if="showResend" class="rounded-xl bg-gray-50 p-4">
      <p class="mb-2 text-sm text-gray-600">Link verifikasi hilang atau kedaluwarsa?</p>
      <ResendVerificationForm :initial-email="form.email.trim()" />
    </div>

    <!-- Submit -->
    <button
      type="submit"
      :disabled="!isFormValid || isSubmitting"
      class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
    >
      {{ isSubmitting ? 'Signing in...' : 'Sign In' }}
    </button>

    <p class="text-center">
      <button
        type="button"
        class="text-sm text-gray-400 hover:text-gray-600"
        @click="router.push({ name: 'auth-forgot-password' })"
      >
        Forgot Password?
      </button>
    </p>
  </form>
</template>

<!-- frontend/src/modules/auth/views/ForgotPasswordView.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { authService } from '../services/auth.service';
import type { ApiValidationErrorResponse } from '../types/register.types';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import { BRAND_NAME } from '@/shared/config/brand';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

const router = useRouter();

const email = ref('');
const isSubmitting = ref(false);
const generalError = ref('');
const serverErrors = ref<Record<string, string>>({});
const isSubmitted = ref(false);

const isFormValid = computed(() => email.value.trim().length > 0);

function clearFieldError() {
  if (serverErrors.value.email) {
    delete serverErrors.value.email;
  }
}

async function handleSubmit() {
  generalError.value = '';
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    await authService.forgotPassword({ email: email.value });
    // Backend SELALU balas pesan sukses generik yang sama (baik email
    // terdaftar maupun tidak) -- jadi UI di sini juga selalu tampilkan
    // state sukses yang sama, tidak membedakan.
    isSubmitted.value = true;
  } catch (err) {
    if (axios.isAxiosError<ApiValidationErrorResponse>(err)) {
      const data = err.response?.data;
      if (err.response?.status === 400 && data?.errors) {
        serverErrors.value = data.errors;
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

function backToSignIn() {
  router.push({ name: 'auth-signin' });
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
        <p class="mt-2 text-base text-gray-400">Forgot your password?</p>
      </div>

      <!-- Success state -->
      <div v-if="isSubmitted" class="text-center">
        <div class="mb-4 flex justify-center text-green-500">
          <CheckCircleIcon :size="48" />
        </div>
        <p class="text-base font-medium text-gray-900">Cek email kamu</p>
        <p class="mt-2 text-sm text-gray-500">
          Kalau email <span class="font-medium text-gray-700">{{ email }}</span> terdaftar,
          kami sudah mengirim link reset password ke sana.
        </p>
        <button
          type="button"
          class="mt-8 w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
          @click="backToSignIn"
        >
          Back to Sign In
        </button>
      </div>

      <!-- Form state -->
      <form v-else class="space-y-6" @submit.prevent="handleSubmit">
        <p class="text-sm text-gray-500">
          Masukkan email yang terdaftar, kami akan kirim link untuk reset password kamu.
        </p>

        <div>
          <label for="forgot-email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
          <input
            id="forgot-email"
            v-model="email"
            type="email"
            placeholder="you@example.com"
            class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
            :class="{ 'border-red-500': serverErrors.email }"
            @input="clearFieldError"
          />
          <p v-if="serverErrors.email" class="mt-1.5 text-sm text-red-600">
            {{ serverErrors.email }}
          </p>
        </div>

        <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

        <button
          type="submit"
          :disabled="!isFormValid || isSubmitting"
          class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isSubmitting ? 'Sending...' : 'Send Reset Link' }}
        </button>

        <p class="text-center">
          <button
            type="button"
            class="text-sm text-gray-400 hover:text-gray-600"
            @click="backToSignIn"
          >
            Back to Sign In
          </button>
        </p>
      </form>
    </div>
  </div>
</template>

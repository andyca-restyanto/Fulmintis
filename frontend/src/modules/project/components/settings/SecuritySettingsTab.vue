<!-- frontend/src/modules/project/components/settings/SecuritySettingsTab.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { authService } from '@/modules/auth';
import { tokenStorage } from '@/shared/services/tokenStorage';
import MailIcon from '@/shared/components/icons/MailIcon.vue';
import ShieldIcon from '@/shared/components/icons/ShieldIcon.vue';
import EyeIcon from '@/shared/components/icons/EyeIcon.vue';
import EyeOffIcon from '@/shared/components/icons/EyeOffIcon.vue';

const email = ref('');

onMounted(async () => {
  try {
    const profile = await authService.getProfile();
    email.value = profile.email;
  } catch {
    // Card "Reset via Email" jadi tidak bisa dipakai (butuh email), tapi
    // Update Password tetap jalan normal krn tidak butuh email.
  }
});

// ---- Update Password ----
// NOTE: mockup TIDAK menampilkan field "Current Password", tapi backend
// (ChangePasswordRequestDTO) MEWAJIBKAN-nya sbg validasi keamanan standar
// (user harus tau password lama sebelum bisa ganti password baru saat
// masih login) -- jadi field ini SENGAJA ditambahkan di sini, beda dari
// mockup, supaya endpoint-nya beneran bisa dipakai.
const currentPassword = ref('');
const newPassword = ref('');
const confirmNewPassword = ref('');
const showCurrentPassword = ref(false);
const showNewPassword = ref(false);
const showConfirmPassword = ref(false);

const isUpdatingPassword = ref(false);
const passwordError = ref('');
const passwordFieldErrors = ref<Record<string, string>>({});
const passwordSuccess = ref('');

async function handleUpdatePassword() {
  passwordError.value = '';
  passwordFieldErrors.value = {};
  passwordSuccess.value = '';

  if (!currentPassword.value || !newPassword.value || !confirmNewPassword.value) return;

  isUpdatingPassword.value = true;
  try {
    const result = await authService.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      confirmNewPassword: confirmNewPassword.value,
    });
    // Token LAMA sudah tidak berlaku (backend mencabutnya saat password
    // berubah) -- ganti dgn token baru supaya sesi ini tidak terputus.
    if (result.accessToken) {
      tokenStorage.setToken(result.accessToken);
    }
    passwordSuccess.value = result.message;
    currentPassword.value = '';
    newPassword.value = '';
    confirmNewPassword.value = '';
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string; errors?: Record<string, string> };
      if (err.response?.status === 400 && data?.errors) {
        passwordFieldErrors.value = data.errors;
      } else {
        // Mencakup IncorrectCurrentPasswordException (400, "Password saat ini salah.")
        passwordError.value = data?.message ?? 'Gagal mengubah password.';
      }
    } else {
      passwordError.value = 'Tidak dapat terhubung ke server.';
    }
  } finally {
    isUpdatingPassword.value = false;
  }
}

// ---- Reset via Email (flow lupa password, reuse forgot-password) ----
const isSendingReset = ref(false);
const resetMessage = ref('');

async function handleSendResetLink() {
  if (!email.value) return;

  isSendingReset.value = true;
  resetMessage.value = '';
  try {
    const result = await authService.forgotPassword({ email: email.value });
    resetMessage.value = result.message;
  } catch {
    resetMessage.value = 'Gagal mengirim link reset, coba lagi.';
  } finally {
    isSendingReset.value = false;
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Account -->
    <div class="rounded-2xl border border-gray-200 p-8">
      <h2 class="flex items-center gap-2 text-lg font-bold text-gray-900">
        <MailIcon :size="18" />
        Account
      </h2>

      <div class="mt-6">
        <p class="text-sm text-gray-400">Email</p>
        <p class="mt-1 text-sm font-medium text-gray-900">{{ email || '...' }}</p>
      </div>
    </div>

    <!-- Update Password -->
    <div class="rounded-2xl border border-gray-200 p-8">
      <h2 class="flex items-center gap-2 text-lg font-bold text-gray-900">
        <ShieldIcon :size="18" />
        Update Password
      </h2>
      <p class="mt-1 text-sm text-gray-400">Change your password. You must be recently logged in.</p>

      <form class="mt-6 space-y-5" @submit.prevent="handleUpdatePassword">
        <div>
          <label for="current-password" class="mb-2 block text-sm font-semibold text-gray-900">
            Current Password
          </label>
          <div class="relative">
            <input
              id="current-password"
              v-model="currentPassword"
              :type="showCurrentPassword ? 'text' : 'password'"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 pr-11 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': passwordFieldErrors.currentPassword }"
            />
            <button
              type="button"
              class="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              @click="showCurrentPassword = !showCurrentPassword"
            >
              <EyeOffIcon v-if="showCurrentPassword" :size="18" />
              <EyeIcon v-else :size="18" />
            </button>
          </div>
          <p v-if="passwordFieldErrors.currentPassword" class="mt-1.5 text-sm text-red-600">
            {{ passwordFieldErrors.currentPassword }}
          </p>
        </div>

        <div>
          <label for="new-password" class="mb-2 block text-sm font-semibold text-gray-900">
            New Password
          </label>
          <div class="relative">
            <input
              id="new-password"
              v-model="newPassword"
              :type="showNewPassword ? 'text' : 'password'"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 pr-11 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': passwordFieldErrors.newPassword }"
            />
            <button
              type="button"
              class="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              @click="showNewPassword = !showNewPassword"
            >
              <EyeOffIcon v-if="showNewPassword" :size="18" />
              <EyeIcon v-else :size="18" />
            </button>
          </div>
          <p v-if="passwordFieldErrors.newPassword" class="mt-1.5 text-sm text-red-600">
            {{ passwordFieldErrors.newPassword }}
          </p>
        </div>

        <div>
          <label for="confirm-new-password" class="mb-2 block text-sm font-semibold text-gray-900">
            Confirm New Password
          </label>
          <div class="relative">
            <input
              id="confirm-new-password"
              v-model="confirmNewPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              class="w-full rounded-xl border border-gray-200 px-4 py-3 pr-11 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
              :class="{ 'border-red-500': passwordFieldErrors.confirmNewPassword }"
            />
            <button
              type="button"
              class="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              @click="showConfirmPassword = !showConfirmPassword"
            >
              <EyeOffIcon v-if="showConfirmPassword" :size="18" />
              <EyeIcon v-else :size="18" />
            </button>
          </div>
          <p v-if="passwordFieldErrors.confirmNewPassword" class="mt-1.5 text-sm text-red-600">
            {{ passwordFieldErrors.confirmNewPassword }}
          </p>
        </div>

        <p v-if="passwordError" class="text-sm text-red-600">{{ passwordError }}</p>
        <p v-if="passwordSuccess" class="text-sm text-emerald-600">{{ passwordSuccess }}</p>

        <button
          type="submit"
          :disabled="isUpdatingPassword"
          class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
        >
          {{ isUpdatingPassword ? 'Updating...' : 'Update Password' }}
        </button>
      </form>
    </div>

    <!-- Reset via Email -->
    <div class="rounded-2xl border border-gray-200 p-8">
      <h2 class="text-lg font-bold text-gray-900">Reset via Email</h2>
      <p class="mt-1 text-sm text-gray-400">Forgot your current password? We'll send a secure reset link.</p>

      <button
        type="button"
        :disabled="isSendingReset || !email"
        class="mt-5 rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300"
        @click="handleSendResetLink"
      >
        {{ isSendingReset ? 'Sending...' : 'Send Reset Link' }}
      </button>

      <p v-if="resetMessage" class="mt-3 text-sm text-emerald-600">{{ resetMessage }}</p>
    </div>
  </div>
</template>

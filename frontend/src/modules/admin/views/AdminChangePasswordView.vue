<!-- frontend/src/modules/admin/views/AdminChangePasswordView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { authService } from '@/modules/auth/services/auth.service';
import { usePasswordValidation } from '@/modules/auth/composables/usePasswordValidation';
import { adminAuthService } from '../services/adminAuth.service';
import { readAdminError } from '../utils/adminErrors';
import AdminPasswordField from '../components/AdminPasswordField.vue';
import { tokenStorage } from '@/shared/services/tokenStorage';
import ArrowLeftIcon from '@/shared/components/icons/ArrowLeftIcon.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

// Alur sama dengan "Update Password" user (Settings > Security): POST /api/auth/change-password,
// lalu token LAMA diganti token BARU dari respons agar sesi tidak terputus (peran tetap ADMIN).
const router = useRouter();

const currentPassword = ref('');
const newPassword = ref('');
const confirmNewPassword = ref('');

const isSubmitting = ref(false);
const serverErrors = ref<Record<string, string>>({});
const generalError = ref('');
const successMessage = ref('');

const { isPasswordValid } = usePasswordValidation(newPassword);
const passwordsMatch = computed(
  () => confirmNewPassword.value.length > 0 && newPassword.value === confirmNewPassword.value
);
const isFormValid = computed(
  () => currentPassword.value.length > 0 && isPasswordValid.value && passwordsMatch.value
);
const confirmError = computed(() =>
  confirmNewPassword.value.length > 0 && !passwordsMatch.value ? 'Konfirmasi password tidak sama' : ''
);

function clearFieldError(field: string) {
  if (serverErrors.value[field]) {
    delete serverErrors.value[field];
  }
}

function onCurrentInput(value: string) {
  currentPassword.value = value;
  clearFieldError('currentPassword');
}
function onNewInput(value: string) {
  newPassword.value = value;
  clearFieldError('newPassword');
}
function onConfirmInput(value: string) {
  confirmNewPassword.value = value;
  clearFieldError('confirmNewPassword');
}

async function handleSubmit() {
  generalError.value = '';
  successMessage.value = '';
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    const result = await authService.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      confirmNewPassword: confirmNewPassword.value,
    });
    // Token lama sudah dicabut backend saat password berubah -> pakai yang baru.
    if (result.accessToken) {
      tokenStorage.setToken(result.accessToken);
    }
    successMessage.value = result.message;
    currentPassword.value = '';
    newPassword.value = '';
    confirmNewPassword.value = '';
  } catch (err) {
    const info = readAdminError(err, 'Gagal mengubah password.');
    if (info.status === 400 && Object.keys(info.fieldErrors).length > 0) {
      serverErrors.value = info.fieldErrors;
    } else if (info.status === 400) {
      // IncorrectCurrentPasswordException: 400 "Password saat ini salah." (bukan 401, jadi tidak logout)
      serverErrors.value = { currentPassword: info.message };
    } else {
      // 429 (terlalu banyak percobaan), 403, jaringan, dst.
      generalError.value = info.message;
    }
  } finally {
    isSubmitting.value = false;
  }
}

// ---- Lupa password saat ini: kirim link reset ke email admin sendiri (flow forgot-password) ----
const adminEmail = ref('');
const isSendingReset = ref(false);
const resetMessage = ref('');

onMounted(async () => {
  try {
    const me = await adminAuthService.getMe();
    adminEmail.value = me.email;
  } catch {
    // 401 -> interceptor sudah mengarahkan ke /admin/signin. Error lain: tautan reset disembunyikan.
  }
});

async function handleSendResetLink() {
  if (!adminEmail.value || isSendingReset.value) return;

  isSendingReset.value = true;
  resetMessage.value = '';
  try {
    const result = await authService.forgotPassword({ email: adminEmail.value });
    resetMessage.value = result.message;
  } catch (err) {
    resetMessage.value = readAdminError(err, 'Gagal mengirim link reset, coba lagi.').message;
  } finally {
    isSendingReset.value = false;
  }
}
</script>

<template>
  <div class="max-w-lg">
    <button
      type="button"
      class="mb-4 flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800"
      @click="router.push({ name: 'admin-dashboard' })"
    >
      <ArrowLeftIcon :size="16" />
      Kembali
    </button>

    <h1 class="text-2xl font-bold text-gray-900">Ubah Password</h1>
    <p class="mt-1 text-gray-500">
      Masukkan password saat ini, lalu password baru. Perangkat lain yang sedang login akan keluar otomatis.
    </p>

    <form class="mt-8 space-y-6" @submit.prevent="handleSubmit">
      <AdminPasswordField
        id="current-password"
        label="Password Saat Ini"
        :model-value="currentPassword"
        :error="serverErrors.currentPassword"
        autocomplete="current-password"
        @update:model-value="onCurrentInput"
      />

      <AdminPasswordField
        id="new-password"
        label="Password Baru"
        :model-value="newPassword"
        :error="serverErrors.newPassword"
        show-checklist
        @update:model-value="onNewInput"
      />

      <AdminPasswordField
        id="confirm-new-password"
        label="Konfirmasi Password Baru"
        :model-value="confirmNewPassword"
        :error="serverErrors.confirmNewPassword || confirmError"
        @update:model-value="onConfirmInput"
      />

      <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
      <p v-if="successMessage" class="flex items-start gap-2 text-sm text-green-600">
        <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
        {{ successMessage }}
      </p>

      <button
        type="submit"
        :disabled="!isFormValid || isSubmitting"
        class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
      >
        {{ isSubmitting ? 'Menyimpan...' : 'Ubah Password' }}
      </button>
    </form>

    <div v-if="adminEmail" class="mt-8 border-t border-gray-100 pt-6">
      <p class="text-sm text-gray-500">
        Lupa password saat ini? Kami bisa mengirim link reset ke
        <span class="font-medium text-gray-700">{{ adminEmail }}</span>.
      </p>
      <button
        type="button"
        :disabled="isSendingReset"
        class="mt-3 text-sm font-semibold text-gray-900 underline-offset-2 hover:underline disabled:cursor-not-allowed disabled:text-gray-400"
        @click="handleSendResetLink"
      >
        {{ isSendingReset ? 'Mengirim...' : 'Kirim link reset ke email saya' }}
      </button>
      <p v-if="resetMessage" class="mt-2 text-sm text-gray-600">{{ resetMessage }}</p>
    </div>
  </div>
</template>

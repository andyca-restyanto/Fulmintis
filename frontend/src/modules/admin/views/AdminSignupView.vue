<!-- frontend/src/modules/admin/views/AdminSignupView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { adminAuthService } from '../services/adminAuth.service';
import { readAdminError } from '../utils/adminErrors';
import type { AdminRegisterRequest, AdminRegistrationStatus } from '../types/admin-auth.types';
import AdminAuthShell from '../components/AdminAuthShell.vue';
import AdminPasswordField from '../components/AdminPasswordField.vue';
import { usePasswordValidation } from '@/modules/auth/composables/usePasswordValidation';
import ResendVerificationForm from '@/modules/auth/components/ResendVerificationForm.vue';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';

const router = useRouter();

// checking -> menanyakan backend apakah pendaftaran admin pertama masih terbuka
// open     -> tampilkan form
// closed   -> sudah ada admin: admin berikutnya hanya lewat undangan
// error    -> status tidak bisa diambil (jaringan)
// success  -> terdaftar, menunggu verifikasi email
const pageState = ref<'checking' | 'open' | 'closed' | 'error' | 'success'>('checking');
const status = ref<AdminRegistrationStatus | null>(null);

const form = ref<AdminRegisterRequest>({
  name: '',
  email: '',
  password: '',
  confirmPassword: '',
  bootstrapCode: '',
});

const isSubmitting = ref(false);
const serverErrors = ref<Record<string, string>>({});
const generalError = ref('');
const successMessage = ref('');
const registeredEmail = ref('');

const passwordRef = computed(() => form.value.password);
const { isPasswordValid } = usePasswordValidation(passwordRef);

const bootstrapCodeRequired = computed(() => status.value?.bootstrapCodeRequired === true);

const passwordsMatch = computed(
  () => form.value.confirmPassword.length > 0 && form.value.password === form.value.confirmPassword
);

const isFormValid = computed(
  () =>
    form.value.name.trim().length > 0 &&
    form.value.email.trim().length > 0 &&
    isPasswordValid.value &&
    passwordsMatch.value &&
    (!bootstrapCodeRequired.value || (form.value.bootstrapCode ?? '').trim().length > 0)
);

async function loadStatus() {
  pageState.value = 'checking';
  try {
    status.value = await adminAuthService.getRegistrationStatus();
    pageState.value = status.value.open ? 'open' : 'closed';
  } catch {
    pageState.value = 'error';
  }
}

onMounted(loadStatus);

function clearFieldError(field: string) {
  if (serverErrors.value[field]) {
    delete serverErrors.value[field];
  }
}

async function handleSubmit() {
  generalError.value = '';
  successMessage.value = '';
  serverErrors.value = {};

  if (!isFormValid.value) return;

  isSubmitting.value = true;
  try {
    const response = await adminAuthService.register({
      name: form.value.name.trim(),
      email: form.value.email.trim(),
      password: form.value.password,
      confirmPassword: form.value.confirmPassword,
      bootstrapCode: bootstrapCodeRequired.value ? (form.value.bootstrapCode ?? '').trim() : null,
    });
    successMessage.value = response.message;
    registeredEmail.value = response.email;
    form.value = { name: '', email: '', password: '', confirmPassword: '', bootstrapCode: '' };
    pageState.value = 'success';
  } catch (err) {
    const info = readAdminError(err);
    if (info.status === 400 && Object.keys(info.fieldErrors).length > 0) {
      serverErrors.value = info.fieldErrors;
    } else if (info.status === 403) {
      // 403 bisa berarti "sudah ada admin" ATAU "kode salah". Tanyakan status ke backend
      // alih-alih menebak dari teks pesan.
      try {
        status.value = await adminAuthService.getRegistrationStatus();
      } catch {
        status.value = null;
      }
      if (status.value && !status.value.open) {
        pageState.value = 'closed';
      } else {
        generalError.value = info.message;
      }
    } else {
      generalError.value = info.message; // 409 email sudah terdaftar, 429, jaringan, dst.
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AdminAuthShell subtitle="Daftarkan admin pertama">
    <p v-if="pageState === 'checking'" class="text-center text-sm text-gray-400">
      Memeriksa status pendaftaran...
    </p>

    <!-- Sudah ada admin -->
    <div v-else-if="pageState === 'closed'" class="text-center">
      <div class="mb-4 flex justify-center text-gray-400">
        <AlertCircleIcon :size="48" />
      </div>
      <p class="text-base font-medium text-gray-900">Pendaftaran admin ditutup</p>
      <p class="mt-2 text-sm text-gray-500">
        Admin sudah terdaftar. Untuk menambah admin, minta admin yang ada mengirim undangan ke email kamu.
      </p>
      <button
        type="button"
        class="mt-8 w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
        @click="router.push({ name: 'admin-signin' })"
      >
        Ke Sign In Admin
      </button>
    </div>

    <!-- Status tidak terbaca -->
    <div v-else-if="pageState === 'error'" class="text-center">
      <p class="text-sm text-red-600">Tidak dapat memeriksa status pendaftaran. Periksa koneksi lalu coba lagi.</p>
      <button
        type="button"
        class="mt-6 w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
        @click="loadStatus"
      >
        Coba Lagi
      </button>
    </div>

    <!-- Berhasil -->
    <div v-else-if="pageState === 'success'" class="space-y-4">
      <p class="text-sm text-green-600">{{ successMessage }}</p>
      <div class="rounded-xl bg-gray-50 p-4">
        <p class="mb-2 text-sm text-gray-600">Belum menerima email verifikasi?</p>
        <ResendVerificationForm :initial-email="registeredEmail" />
      </div>
      <button
        type="button"
        class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
        @click="router.push({ name: 'admin-signin' })"
      >
        Ke Sign In Admin
      </button>
    </div>

    <!-- Form -->
    <form v-else class="space-y-6" @submit.prevent="handleSubmit">
      <div>
        <label for="admin-name" class="mb-2 block text-base font-semibold text-gray-900">Nama</label>
        <input
          id="admin-name"
          v-model="form.name"
          type="text"
          placeholder="Nama lengkap"
          class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          :class="{ 'border-red-500': serverErrors.name }"
          @input="clearFieldError('name')"
        />
        <p v-if="serverErrors.name" class="mt-1.5 text-sm text-red-600">{{ serverErrors.name }}</p>
      </div>

      <div>
        <label for="admin-email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
        <input
          id="admin-email"
          v-model="form.email"
          type="email"
          placeholder="admin@example.com"
          class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          :class="{ 'border-red-500': serverErrors.email }"
          @input="clearFieldError('email')"
        />
        <p v-if="serverErrors.email" class="mt-1.5 text-sm text-red-600">{{ serverErrors.email }}</p>
      </div>

      <AdminPasswordField
        id="admin-password"
        v-model="form.password"
        label="Password"
        show-checklist
        :error="serverErrors.password"
        @update:model-value="clearFieldError('password')"
      />

      <div>
        <AdminPasswordField
          id="admin-confirm-password"
          v-model="form.confirmPassword"
          label="Confirm Password"
          :error="serverErrors.confirmPassword"
          @update:model-value="clearFieldError('confirmPassword')"
        />
        <p v-if="form.confirmPassword && !passwordsMatch" class="mt-1.5 text-sm text-red-600">
          Password dan konfirmasi tidak sama
        </p>
      </div>

      <div v-if="bootstrapCodeRequired">
        <label for="admin-bootstrap-code" class="mb-2 block text-base font-semibold text-gray-900">
          Kode pendaftaran
        </label>
        <input
          id="admin-bootstrap-code"
          v-model="form.bootstrapCode"
          type="password"
          autocomplete="off"
          placeholder="Kode dari pemilik sistem"
          class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          :class="{ 'border-red-500': serverErrors.bootstrapCode }"
          @input="clearFieldError('bootstrapCode')"
        />
        <p v-if="serverErrors.bootstrapCode" class="mt-1.5 text-sm text-red-600">
          {{ serverErrors.bootstrapCode }}
        </p>
      </div>

      <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

      <button
        type="submit"
        :disabled="!isFormValid || isSubmitting"
        class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
      >
        {{ isSubmitting ? 'Creating...' : 'Create Admin Account' }}
      </button>

      <p class="text-center text-sm text-gray-400">
        Sudah punya akun admin?
        <button type="button" class="font-medium text-gray-700 hover:text-gray-900" @click="router.push({ name: 'admin-signin' })">
          Sign in
        </button>
      </p>
    </form>
  </AdminAuthShell>
</template>

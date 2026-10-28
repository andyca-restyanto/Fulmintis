<!-- frontend/src/modules/admin/views/AdminSigninView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminAuthService } from '../services/adminAuth.service';
import { readAdminError, isAccountDeactivated, offersVerificationResend } from '../utils/adminErrors';
import AdminAuthShell from '../components/AdminAuthShell.vue';
import AdminPasswordField from '../components/AdminPasswordField.vue';
import VerificationNotice from '@/modules/auth/components/VerificationNotice.vue';
import ResendVerificationForm from '@/modules/auth/components/ResendVerificationForm.vue';
import { tokenStorage } from '@/shared/services/tokenStorage';

const route = useRoute();
const router = useRouter();

const form = ref({ email: '', password: '' });
const isSubmitting = ref(false);
const generalError = ref('');
const showResend = ref(false);
const serverErrors = ref<Record<string, string>>({});

// Link "daftar admin pertama" hanya tampil selama pendaftaran masih terbuka.
const registrationOpen = ref(false);

onMounted(async () => {
  try {
    registrationOpen.value = (await adminAuthService.getRegistrationStatus()).open;
  } catch {
    registrationOpen.value = false; // status tidak terbaca -> sembunyikan link, sign in tetap bisa
  }
});

// ---- Notifikasi dari query string ----
// Redirect backend setelah verifikasi email : /admin/signin?verified=true|false&reason=...
// Setelah reset password / terima undangan  : ?reset=true  /  ?accepted=true
// Sesi berakhir (apiClient, respons 401)    : ?expired=true
const isSessionExpired = route.query.expired === 'true';
const notice = ref<'success' | 'error' | null>(
  route.query.verified === 'true' || route.query.reset === 'true' || route.query.accepted === 'true'
    ? 'success'
    : route.query.verified === 'false' || isSessionExpired
      ? 'error'
      : null
);
const allowResend = route.query.verified === 'false';
const noticeReason = typeof route.query.reason === 'string' ? route.query.reason : null;
const noticeErrorMessage = isSessionExpired ? 'Sesi kamu telah berakhir. Silakan sign in lagi.' : undefined;
const noticeSuccessMessage = computed(() => {
  if (route.query.reset === 'true') return 'Password berhasil diubah! Silakan sign in dengan password baru kamu.';
  if (route.query.accepted === 'true') return 'Password berhasil dibuat! Silakan sign in sebagai admin.';
  return 'Verifikasi email berhasil! Silakan sign in sebagai admin.';
});

function dismissNotice() {
  notice.value = null;
  router.replace({ query: {} });
}

const isFormValid = computed(() => form.value.email.trim().length > 0 && form.value.password.length > 0);

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
    const response = await adminAuthService.login({
      email: form.value.email.trim(),
      password: form.value.password,
    });
    // Lapis kedua di FE: endpoint admin selalu membalas role ADMIN. Kalau bukan, jangan simpan token.
    if (response.role !== 'ADMIN') {
      generalError.value = 'Email atau password salah';
      return;
    }
    tokenStorage.setToken(response.accessToken);
    router.push({ name: 'admin-dashboard' });
  } catch (err) {
    const info = readAdminError(err, 'Email atau password salah');
    if (info.status === 400 && Object.keys(info.fieldErrors).length > 0) {
      serverErrors.value = info.fieldErrors;
    } else if (isAccountDeactivated(info)) {
      // Akun dinonaktifkan admin lain: kirim ulang verifikasi tidak relevan, jadi form-nya tidak ditampilkan.
      generalError.value = info.message || 'Akun admin ini dinonaktifkan. Hubungi admin lain untuk mengaktifkannya kembali.';
    } else if (offersVerificationResend(info)) {
      generalError.value = info.message || 'Akun belum diverifikasi. Silakan cek email kamu.';
      showResend.value = true;
    } else {
      generalError.value = info.message;
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AdminAuthShell subtitle="Sign in sebagai admin">
    <VerificationNotice
      :status="notice"
      :reason="noticeReason"
      :success-message="noticeSuccessMessage"
      :error-message="noticeErrorMessage"
      :allow-resend="allowResend"
      @dismiss="dismissNotice"
    />

    <form class="space-y-6" @submit.prevent="handleSubmit">
      <div>
        <label for="admin-login-email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
        <input
          id="admin-login-email"
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
        id="admin-login-password"
        v-model="form.password"
        label="Password"
        :error="serverErrors.password"
        @update:model-value="clearFieldError('password')"
      />

      <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
      <div v-if="showResend" class="rounded-xl bg-gray-50 p-4">
        <p class="mb-2 text-sm text-gray-600">Link verifikasi hilang atau kedaluwarsa?</p>
        <ResendVerificationForm :initial-email="form.email.trim()" />
      </div>

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
          @click="router.push({ name: 'admin-forgot-password' })"
        >
          Forgot Password?
        </button>
      </p>

      <p v-if="registrationOpen" class="text-center text-sm text-gray-400">
        Belum ada admin?
        <button type="button" class="font-medium text-gray-700 hover:text-gray-900" @click="router.push({ name: 'admin-signup' })">
          Daftarkan admin pertama
        </button>
      </p>
    </form>
  </AdminAuthShell>
</template>

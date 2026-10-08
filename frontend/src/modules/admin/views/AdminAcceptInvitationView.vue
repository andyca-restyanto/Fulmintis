<!-- frontend/src/modules/admin/views/AdminAcceptInvitationView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminAuthService } from '../services/adminAuth.service';
import { readAdminError } from '../utils/adminErrors';
import AdminAuthShell from '../components/AdminAuthShell.vue';
import AdminPasswordField from '../components/AdminPasswordField.vue';
import { usePasswordValidation } from '@/modules/auth/composables/usePasswordValidation';
import AlertCircleIcon from '@/shared/components/icons/AlertCircleIcon.vue';

const route = useRoute();
const router = useRouter();

const token = typeof route.query.token === 'string' ? route.query.token : '';

// checking -> memvalidasi token ke backend
// valid    -> tampilkan form buat password
// invalid  -> link salah / kedaluwarsa / sudah dipakai
const tokenState = ref<'checking' | 'valid' | 'invalid'>('checking');
const inviteeEmail = ref('');
const inviteeName = ref<string | null>(null);

const newPassword = ref('');
const confirmPassword = ref('');
const isSubmitting = ref(false);
const generalError = ref('');
const serverErrors = ref<Record<string, string>>({});

const passwordRef = computed(() => newPassword.value);
const { isPasswordValid } = usePasswordValidation(passwordRef);

const passwordsMatch = computed(
  () => confirmPassword.value.length > 0 && newPassword.value === confirmPassword.value
);
const isFormValid = computed(() => isPasswordValid.value && passwordsMatch.value);

onMounted(async () => {
  if (!token) {
    tokenState.value = 'invalid';
    return;
  }
  try {
    const result = await adminAuthService.validateInvitation(token);
    inviteeEmail.value = result.email;
    inviteeName.value = result.name;
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
    await adminAuthService.acceptInvitation({
      token,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    });
    router.push({ name: 'admin-signin', query: { accepted: 'true' } });
  } catch (err) {
    const info = readAdminError(err);
    if (info.status === 400 && Object.keys(info.fieldErrors).length > 0) {
      serverErrors.value = info.fieldErrors;
    } else if (info.status === 400) {
      // Link jadi tidak berlaku di antara validasi awal & submit (dipakai di tab lain / kedaluwarsa).
      tokenState.value = 'invalid';
    } else {
      generalError.value = info.message;
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AdminAuthShell subtitle="Terima undangan admin">
    <p v-if="tokenState === 'checking'" class="text-center text-sm text-gray-400">
      Memeriksa link undangan...
    </p>

    <div v-else-if="tokenState === 'invalid'" class="text-center">
      <div class="mb-4 flex justify-center text-red-500">
        <AlertCircleIcon :size="48" />
      </div>
      <p class="text-base font-medium text-gray-900">Link undangan tidak valid</p>
      <p class="mt-2 text-sm text-gray-500">
        Link ini tidak valid, sudah kedaluwarsa, atau sudah dipakai. Minta admin yang mengundang kamu untuk
        mengirim ulang undangan.
      </p>
      <button
        type="button"
        class="mt-8 w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800"
        @click="router.push({ name: 'admin-signin' })"
      >
        Ke Sign In Admin
      </button>
    </div>

    <form v-else class="space-y-6" @submit.prevent="handleSubmit">
      <p class="text-sm text-gray-600">
        <template v-if="inviteeName">Halo <span class="font-semibold">{{ inviteeName }}</span>. </template>
        Buat password untuk akun admin <span class="font-semibold">{{ inviteeEmail }}</span>.
      </p>

      <AdminPasswordField
        id="invite-new-password"
        v-model="newPassword"
        label="Password"
        show-checklist
        :error="serverErrors.newPassword"
        @update:model-value="clearFieldError('newPassword')"
      />

      <div>
        <AdminPasswordField
          id="invite-confirm-password"
          v-model="confirmPassword"
          label="Confirm Password"
          :error="serverErrors.confirmPassword"
          @update:model-value="clearFieldError('confirmPassword')"
        />
        <p v-if="confirmPassword && !passwordsMatch" class="mt-1.5 text-sm text-red-600">
          Password dan konfirmasi tidak sama
        </p>
      </div>

      <p v-if="serverErrors.token" class="text-sm text-red-600">{{ serverErrors.token }}</p>
      <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>

      <button
        type="submit"
        :disabled="!isFormValid || isSubmitting"
        class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
      >
        {{ isSubmitting ? 'Menyimpan...' : 'Buat Password' }}
      </button>
    </form>
  </AdminAuthShell>
</template>

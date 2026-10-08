<!-- frontend/src/modules/admin/views/AdminInviteView.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { adminManagementService } from '../services/adminManagement.service';
import { readAdminError } from '../utils/adminErrors';
import type { AdminInviteRequest } from '../types/admin-auth.types';
import ArrowLeftIcon from '@/shared/components/icons/ArrowLeftIcon.vue';
import CheckCircleIcon from '@/shared/components/icons/CheckCircleIcon.vue';

const router = useRouter();

const form = ref<AdminInviteRequest>({ name: '', email: '' });
const isSubmitting = ref(false);
const serverErrors = ref<Record<string, string>>({});
const generalError = ref('');
const successMessage = ref('');

const isFormValid = computed(
  () => form.value.name.trim().length > 0 && form.value.email.trim().length > 0
);

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
    const response = await adminManagementService.invite({
      name: form.value.name.trim(),
      email: form.value.email.trim(),
    });
    successMessage.value = response.message;
    form.value = { name: '', email: '' };
  } catch (err) {
    const info = readAdminError(err);
    if (info.status === 400 && Object.keys(info.fieldErrors).length > 0) {
      serverErrors.value = info.fieldErrors;
    } else {
      // 409 (email sudah terdaftar / sudah jadi admin), 403, 429, jaringan, dst.
      generalError.value = info.message;
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <div class="max-w-lg">
    <button
      type="button"
      class="mb-4 flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800"
      @click="router.push({ name: 'admin-admins' })"
    >
      <ArrowLeftIcon :size="16" />
      Kembali
    </button>

    <h1 class="text-2xl font-bold text-gray-900">Tambah Admin</h1>
    <p class="mt-1 text-gray-500">
      Kami mengirim link ke email ini untuk membuat password. Link berlaku 24 jam dan hanya bisa dipakai sekali.
    </p>

    <form class="mt-8 space-y-6" @submit.prevent="handleSubmit">
      <div>
        <label for="invite-name" class="mb-2 block text-base font-semibold text-gray-900">Nama</label>
        <input
          id="invite-name"
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
        <label for="invite-email" class="mb-2 block text-base font-semibold text-gray-900">Email</label>
        <input
          id="invite-email"
          v-model="form.email"
          type="email"
          placeholder="admin@example.com"
          class="w-full rounded-xl border border-gray-200 px-4 py-3.5 text-base placeholder-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
          :class="{ 'border-red-500': serverErrors.email }"
          @input="clearFieldError('email')"
        />
        <p v-if="serverErrors.email" class="mt-1.5 text-sm text-red-600">{{ serverErrors.email }}</p>
      </div>

      <p v-if="generalError" class="text-sm text-red-600">{{ generalError }}</p>
      <div v-if="successMessage" class="text-sm text-green-600">
        <p class="flex items-start gap-2">
          <CheckCircleIcon :size="18" class="mt-0.5 shrink-0" />
          {{ successMessage }}
        </p>
        <button
          type="button"
          class="mt-2 font-semibold text-gray-900 underline-offset-2 hover:underline"
          @click="router.push({ name: 'admin-admins' })"
        >
          Lihat daftar admin
        </button>
      </div>

      <button
        type="submit"
        :disabled="!isFormValid || isSubmitting"
        class="w-full rounded-2xl bg-gray-900 py-3.5 text-base font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
      >
        {{ isSubmitting ? 'Mengirim...' : 'Kirim Undangan' }}
      </button>
    </form>
  </div>
</template>

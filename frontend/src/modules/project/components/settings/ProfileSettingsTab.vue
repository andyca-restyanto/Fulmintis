<!-- frontend/src/modules/project/components/settings/ProfileSettingsTab.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import axios from 'axios';
import { authService } from '@/modules/auth';
import UserIcon from '@/shared/components/icons/UserIcon.vue';
import MailIcon from '@/shared/components/icons/MailIcon.vue';

const originalName = ref('');
const name = ref('');
const email = ref('');
const isLoading = ref(true);
const isSaving = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const isDirty = computed(() => name.value.trim() !== originalName.value.trim());
const canSave = computed(() => isDirty.value && name.value.trim().length > 0 && !isSaving.value);

onMounted(async () => {
  try {
    const profile = await authService.getProfile();
    originalName.value = profile.name ?? '';
    name.value = profile.name ?? '';
    email.value = profile.email;
  } catch {
    errorMessage.value = 'Gagal memuat profil.';
  } finally {
    isLoading.value = false;
  }
});

async function handleSave() {
  if (!canSave.value) return;

  errorMessage.value = '';
  successMessage.value = '';
  isSaving.value = true;
  try {
    const updated = await authService.updateProfile({ name: name.value.trim() });
    originalName.value = updated.name ?? '';
    name.value = updated.name ?? '';
    successMessage.value = 'Profil berhasil diperbarui.';
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string };
      errorMessage.value = data?.message ?? 'Gagal menyimpan perubahan.';
    } else {
      errorMessage.value = 'Tidak dapat terhubung ke server.';
    }
  } finally {
    isSaving.value = false;
  }
}
</script>

<template>
  <div class="rounded-2xl border border-gray-200 p-8">
    <h2 class="flex items-center gap-2 text-lg font-bold text-gray-900">
      <UserIcon :size="18" />
      Profile
    </h2>
    <p class="mt-1 text-sm text-gray-400">Update your personal information.</p>

    <div v-if="!isLoading" class="mt-6 space-y-5">
      <div>
        <label for="settings-fullname" class="mb-2 block text-sm font-semibold text-gray-900">
          Full Name
        </label>
        <input
          id="settings-fullname"
          v-model="name"
          type="text"
          class="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-100"
        />
      </div>

      <div>
        <label class="mb-2 flex items-center gap-1.5 text-sm font-semibold text-gray-900">
          <MailIcon :size="14" />
          Email
        </label>
        <input
          :value="email"
          type="text"
          disabled
          class="w-full cursor-not-allowed rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-400"
        />
        <p class="mt-1.5 text-xs text-gray-400">Email cannot be changed.</p>
      </div>

      <p v-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>
      <p v-if="successMessage" class="text-sm text-emerald-600">{{ successMessage }}</p>

      <button
        type="button"
        :disabled="!canSave"
        class="rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-300"
        @click="handleSave"
      >
        {{ isSaving ? 'Saving...' : 'Save Changes' }}
      </button>
    </div>

    <p v-else class="mt-6 text-sm text-gray-400">Memuat...</p>
  </div>
</template>

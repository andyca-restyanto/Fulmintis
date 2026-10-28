<!-- frontend/src/shared/views/NotFoundView.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { tokenStorage } from '@/shared/services/tokenStorage';
import BugIcon from '@/shared/components/icons/BugIcon.vue';

// Tujuan tombol menyesuaikan status login: sudah login -> dashboard,
// belum -> halaman sign in.
const target = computed(() =>
  tokenStorage.hasValidToken()
    ? { name: 'dashboard', label: 'Kembali ke Dashboard' }
    : { name: 'auth-signin', label: 'Ke halaman Sign In' }
);
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center bg-gray-50 px-4 text-center">
    <BugIcon :size="40" class="text-gray-400" />
    <h1 class="mt-4 text-5xl font-bold text-gray-900">404</h1>
    <p class="mt-2 text-base text-gray-500">Halaman yang kamu cari tidak ditemukan.</p>
    <RouterLink
      :to="{ name: target.name }"
      class="mt-6 rounded-2xl bg-gray-900 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
    >
      {{ target.label }}
    </RouterLink>
  </div>
</template>

<!-- frontend/src/modules/admin/views/AdminDashboardView.vue -->
<script setup lang="ts">
import { useRouter } from 'vue-router';
import UserPlusIcon from '@/shared/components/icons/UserPlusIcon.vue';

const router = useRouter();
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900">Dashboard Admin</h1>
    <p class="mt-1 text-gray-500">Kelola akun administrator. Menu lain di samping akan tersedia segera.</p>

    <div class="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div class="rounded-2xl border border-gray-200 p-6">
        <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-700">
          <UserPlusIcon :size="20" />
        </span>
        <p class="mt-4 text-base font-semibold text-gray-900">Tambah Admin</p>
        <p class="mt-1 text-sm text-gray-500">
          Undang admin baru lewat email. Penerima membuat passwordnya sendiri lewat link undangan.
        </p>
        <button
          type="button"
          class="mt-5 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
          @click="router.push({ name: 'admin-invite' })"
        >
          Tambah Admin
        </button>
      </div>
    </div>
  </div>
</template>

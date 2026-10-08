<!-- frontend/src/modules/admin/components/AdminSidebar.vue -->
<script setup lang="ts">
import type { Component } from 'vue';
import { useRoute } from 'vue-router';
import { ADMIN_MENU, isMenuActive, type AdminMenuIconKey } from '../utils/adminMenu';
import ShieldIcon from '@/shared/components/icons/ShieldIcon.vue';
import UsersIcon from '@/shared/components/icons/UsersIcon.vue';
import ActivityIcon from '@/shared/components/icons/ActivityIcon.vue';
import CreditCardIcon from '@/shared/components/icons/CreditCardIcon.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';

const route = useRoute();

// Urutan & label menu ada di utils/adminMenu.ts (murni, diuji). Menu Admin berisi daftar admin;
// menu lainnya masih halaman "Coming soon" (ComingSoonView).
const ICONS: Record<AdminMenuIconKey, Component> = {
  shield: ShieldIcon,
  users: UsersIcon,
  activity: ActivityIcon,
  'credit-card': CreditCardIcon,
  sparkles: SparklesIcon,
};
</script>

<template>
  <aside class="w-64 shrink-0 border-r border-gray-100 px-4 py-6">
    <p class="mb-3 px-3 text-xs font-semibold tracking-wide text-gray-400">MENU</p>
    <nav class="space-y-1">
      <RouterLink
        v-for="item in ADMIN_MENU"
        :key="item.routeName"
        :to="{ name: item.routeName }"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
        :class="
          isMenuActive(item, route.name)
            ? 'bg-gray-100 text-gray-900'
            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
        "
      >
        <component :is="ICONS[item.icon]" :size="18" />
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>

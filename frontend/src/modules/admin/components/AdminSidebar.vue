<!-- frontend/src/modules/admin/components/AdminSidebar.vue -->
<script setup lang="ts">
import type { Component } from 'vue';
import { useRoute } from 'vue-router';
import UsersIcon from '@/shared/components/icons/UsersIcon.vue';
import ActivityIcon from '@/shared/components/icons/ActivityIcon.vue';
import CreditCardIcon from '@/shared/components/icons/CreditCardIcon.vue';
import SparklesIcon from '@/shared/components/icons/SparklesIcon.vue';

const route = useRoute();

interface AdminMenuItem {
  routeName: string;
  label: string;
  icon: Component;
}

// Sementara hanya tampilan menu: setiap menu membuka halaman "Coming soon" (ComingSoonView).
const MENU_ITEMS: AdminMenuItem[] = [
  { routeName: 'admin-users', label: 'User', icon: UsersIcon },
  { routeName: 'admin-user-logs', label: 'Log user', icon: ActivityIcon },
  { routeName: 'admin-payments', label: 'Payment', icon: CreditCardIcon },
  { routeName: 'admin-ai-token-usage', label: 'AI token used', icon: SparklesIcon },
];

function isActive(routeName: string): boolean {
  return route.name === routeName;
}
</script>

<template>
  <aside class="w-64 shrink-0 border-r border-gray-100 px-4 py-6">
    <p class="mb-3 px-3 text-xs font-semibold tracking-wide text-gray-400">MENU</p>
    <nav class="space-y-1">
      <RouterLink
        v-for="item in MENU_ITEMS"
        :key="item.routeName"
        :to="{ name: item.routeName }"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
        :class="
          isActive(item.routeName)
            ? 'bg-gray-100 text-gray-900'
            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
        "
      >
        <component :is="item.icon" :size="18" />
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>

<!-- frontend/src/modules/project/components/ProjectSidebar.vue -->
<script setup lang="ts">
import { computed, type Component } from 'vue';
import { useRoute } from 'vue-router';
import type { ProjectMenuCode } from '../types/project.types';
import LayoutDashboardIcon from '@/shared/components/icons/LayoutDashboardIcon.vue';
import TestRepositoryIcon from '@/shared/components/icons/TestRepositoryIcon.vue';
import PlayIcon from '@/shared/components/icons/PlayIcon.vue';
import BarChartIcon from '@/shared/components/icons/BarChartIcon.vue';
import CodeIcon from '@/shared/components/icons/CodeIcon.vue';
import SettingsIcon from '@/shared/components/icons/SettingsIcon.vue';

const props = defineProps<{
  projectId: string;
  availableMenus: ProjectMenuCode[];
}>();

const route = useRoute();

interface MenuItem {
  code: ProjectMenuCode;
  label: string;
  routeName: string;
  icon: Component;
}

// Definisi SEMUA menu yang mungkin ada. Yang benar-benar ditampilkan cuma
// yang kodenya ada di props.availableMenus (dikirim backend lewat
// ProjectDetailResponseDTO.availableMenus, lihat ProjectServiceImpl di
// backend) -- jadi source of truth "siapa boleh lihat menu apa" ada di
// backend, bukan di-hardcode di sini.
const MENU_ITEMS: MenuItem[] = [
  { code: 'DASHBOARD', label: 'Dashboard', routeName: 'project-dashboard', icon: LayoutDashboardIcon },
  { code: 'TEST_REPOSITORY', label: 'Test Repository', routeName: 'project-test-repository', icon: TestRepositoryIcon },
  { code: 'TEST_RUNS', label: 'Test Runs', routeName: 'project-test-runs', icon: PlayIcon },
  { code: 'REPORT', label: 'Reports', routeName: 'project-reports', icon: BarChartIcon },
  { code: 'AUTOMATION', label: 'Automation', routeName: 'project-automation', icon: CodeIcon },
  { code: 'SETTING', label: 'Settings', routeName: 'project-settings', icon: SettingsIcon },
];

const visibleMenus = computed(() =>
  MENU_ITEMS.filter((item) => props.availableMenus.includes(item.code))
);

function isActive(routeName: string): boolean {
  return route.name === routeName;
}
</script>

<template>
  <aside class="w-64 shrink-0 border-r border-gray-100 px-4 py-6">
    <p class="mb-3 px-3 text-xs font-semibold tracking-wide text-gray-400">MENU</p>
    <nav class="space-y-1">
      <RouterLink
        v-for="item in visibleMenus"
        :key="item.code"
        :to="{ name: item.routeName, params: { projectId } }"
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

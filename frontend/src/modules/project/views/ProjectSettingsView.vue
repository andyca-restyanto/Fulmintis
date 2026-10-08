<!-- frontend/src/modules/project/views/ProjectSettingsView.vue -->
<script setup lang="ts">
import { ref } from 'vue';
import ProfileSettingsTab from '../components/settings/ProfileSettingsTab.vue';
import MembersSettingsTab from '../components/settings/MembersSettingsTab.vue';
import SecuritySettingsTab from '../components/settings/SecuritySettingsTab.vue';

type SettingsTabKey = 'profile' | 'members' | 'security' | 'claude';

const TABS: { key: SettingsTabKey; label: string }[] = [
  { key: 'profile', label: 'Profile' },
  { key: 'members', label: 'Members' },
  { key: 'security', label: 'Security' },
  { key: 'claude', label: 'Claude' },
];

const activeTab = ref<SettingsTabKey>('profile');
</script>

<template>
  <div class="px-8 py-8">
    <h1 class="text-2xl font-bold text-gray-900">Settings</h1>
    <p class="mt-1 text-sm text-gray-400">Manage your project and account</p>

    <div class="mt-6 inline-flex gap-1 rounded-xl bg-gray-100 p-1">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
        :class="
          activeTab === tab.key
            ? 'bg-white text-gray-900 shadow-sm'
            : 'text-gray-500 hover:text-gray-700'
        "
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div class="mt-6 max-w-3xl">
      <ProfileSettingsTab v-if="activeTab === 'profile'" />
      <MembersSettingsTab v-else-if="activeTab === 'members'" />
      <SecuritySettingsTab v-else-if="activeTab === 'security'" />

      <!-- Tab "Claude" ada di mockup tapi TIDAK ada requirement/endpoint
           backend utk ini -- ditampilkan sbg placeholder saja, konsisten
           dgn pola "Generate with AI" (disabled) di Test Repository. -->
      <div v-else class="rounded-2xl border border-gray-200 p-8 text-center text-sm text-gray-400">
        Integrasi Claude belum tersedia &mdash; coming soon.
      </div>
    </div>
  </div>
</template>

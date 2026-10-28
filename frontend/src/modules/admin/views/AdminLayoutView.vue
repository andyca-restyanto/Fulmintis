<!-- frontend/src/modules/admin/views/AdminLayoutView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { adminAuthService } from '../services/adminAuth.service';
import AdminSidebar from '../components/AdminSidebar.vue';
import { tokenStorage } from '@/shared/services/tokenStorage';
import { BRAND_NAME } from '@/shared/config/brand';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import LogOutIcon from '@/shared/components/icons/LogOutIcon.vue';
import ShieldIcon from '@/shared/components/icons/ShieldIcon.vue';

const router = useRouter();

const email = ref('');
const name = ref<string | null>(null);
const isDropdownOpen = ref(false);
const dropdownRoot = ref<HTMLElement | null>(null);

const displayName = computed(() => {
  const customName = name.value?.trim();
  if (customName) return customName;
  if (!email.value) return '';
  const localPart = email.value.split('@')[0] ?? '';
  const firstSegment = localPart.split('.')[0] ?? localPart;
  return firstSegment.charAt(0).toUpperCase() + firstSegment.slice(1);
});
const avatarInitial = computed(() => displayName.value.charAt(0) || '?');

onMounted(async () => {
  try {
    const me = await adminAuthService.getMe();
    email.value = me.email;
    name.value = me.name;
  } catch {
    // 401 -> interceptor apiClient sudah membersihkan sesi & mengarahkan ke /admin/signin.
    // Error lain: header tetap tampil tanpa nama.
  }
});

// Dropdown menutup saat klik di luar atau menekan Esc.
function onDocumentClick(event: MouseEvent) {
  if (!isDropdownOpen.value) return;
  const root = dropdownRoot.value;
  if (root && event.target instanceof Node && !root.contains(event.target)) {
    isDropdownOpen.value = false;
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    isDropdownOpen.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick);
  document.addEventListener('keydown', onDocumentKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick);
  document.removeEventListener('keydown', onDocumentKeydown);
});

function goToChangePassword() {
  isDropdownOpen.value = false;
  router.push({ name: 'admin-change-password' });
}

function handleLogout() {
  isDropdownOpen.value = false;
  tokenStorage.clearToken();
  router.push({ name: 'admin-signin' });
}
</script>

<template>
  <div class="min-h-screen bg-white">
    <header class="flex items-center justify-between border-b border-gray-100 px-8 py-4">
      <div class="flex items-center gap-3">
        <span class="flex items-center gap-2 text-lg font-bold text-gray-900">
          <BugIcon :size="22" />
          {{ BRAND_NAME }}
        </span>
        <span class="rounded-full bg-gray-900 px-2.5 py-0.5 text-xs font-semibold tracking-wide text-white">
          ADMIN
        </span>
      </div>

      <div ref="dropdownRoot" class="relative">
        <button
          type="button"
          class="flex items-center gap-3 rounded-lg px-2 py-1.5 hover:bg-gray-50"
          aria-haspopup="menu"
          :aria-expanded="isDropdownOpen"
          @click="isDropdownOpen = !isDropdownOpen"
        >
          <span class="flex h-9 w-9 items-center justify-center rounded-full bg-gray-900 text-sm font-semibold text-white">
            {{ avatarInitial }}
          </span>
          <span class="text-left">
            <span class="block text-sm font-semibold text-gray-900">{{ displayName }}</span>
            <span class="block text-xs text-gray-400">{{ email }}</span>
          </span>
          <ChevronDownIcon :size="16" class="text-gray-400" />
        </button>

        <div
          v-if="isDropdownOpen"
          role="menu"
          class="absolute right-0 z-10 mt-2 w-48 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
        >
          <button
            type="button"
            role="menuitem"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
            @click="goToChangePassword"
          >
            <ShieldIcon :size="16" />
            Ubah Password
          </button>
          <div class="my-1 border-t border-gray-100" />
          <button
            type="button"
            role="menuitem"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
            @click="handleLogout"
          >
            <LogOutIcon :size="16" />
            Logout
          </button>
        </div>
      </div>
    </header>

    <div class="flex">
      <AdminSidebar />
      <main class="min-w-0 flex-1 px-8 py-8">
        <RouterView />
      </main>
    </div>
  </div>
</template>

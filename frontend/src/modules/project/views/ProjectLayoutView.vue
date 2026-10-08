<!-- frontend/src/modules/project/views/ProjectLayoutView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted, watch, provide } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { projectService } from '../services/project.service';
import type { ProjectDetail, ProjectListItem, ProjectMenuCode } from '../types/project.types';
import { PROJECT_DETAIL_KEY } from '../composables/useProjectDetail';
import { dashboardService } from '@/modules/dashboard';
import { tokenStorage } from '@/shared/services/tokenStorage';
import ProjectSidebar from '../components/ProjectSidebar.vue';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import ToastContainer from '@/shared/components/ToastContainer.vue';
import { BRAND_NAME } from '@/shared/config/brand';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import LogOutIcon from '@/shared/components/icons/LogOutIcon.vue';
import CheckIcon from '@/shared/components/icons/CheckIcon.vue';

const route = useRoute();
const router = useRouter();

const projectId = computed(() => route.params.projectId as string);

const project = ref<ProjectDetail | null>(null);
const isLoadingProject = ref(true);
const loadError = ref('');

const myProjects = ref<ProjectListItem[]>([]);
const isProjectSwitcherOpen = ref(false);

const email = ref('');
const name = ref<string | null>(null); // nama custom user (nullable, lihat DashboardSummaryDTO.name)
const isUserDropdownOpen = ref(false);

// Provide ke SEMUA view anak (ProjectDashboardView, dst) lewat
// useProjectDetail() -- lihat composables/useProjectDetail.ts. Supaya
// pindah menu (Dashboard <-> Test Repository <-> ...) tidak perlu fetch
// ulang GET /api/projects/{id}.
provide(PROJECT_DETAIL_KEY, project);

const displayName = computed(() => {
  const customName = name.value?.trim();
  if (customName) return customName;
  if (!email.value) return '';
  const localPart = email.value.split('@')[0] ?? '';
  const firstSegment = localPart.split('.')[0] ?? localPart;
  return firstSegment.charAt(0).toUpperCase() + firstSegment.slice(1);
});
const avatarInitial = computed(() => displayName.value.charAt(0) || '?');

async function loadProjectDetail(id: string) {
  isLoadingProject.value = true;
  loadError.value = '';
  try {
    project.value = await projectService.getProjectDetail(id);
  } catch {
    // 404 (project tidak ada / bukan member) -- lihat ProjectNotFoundException
    // di backend. Tidak perlu bedakan pesan lebih detail dari itu (anti
    // user-enumeration, sama seperti alasan backend gabungin 2 kasus ini).
    project.value = null;
    loadError.value = 'Project tidak ditemukan, atau kamu bukan member project ini.';
  } finally {
    isLoadingProject.value = false;
  }
}

onMounted(async () => {
  await loadProjectDetail(projectId.value);

  // Dipakai utk isi dropdown "switch project" di topbar -- kalau gagal
  // (jarang terjadi, sama-sama butuh JWT valid yang sudah pasti ada di
  // sini), dropdown cukup kosongkan, tidak fatal utk halaman ini.
  try {
    myProjects.value = await projectService.listMyProjects();
  } catch {
    myProjects.value = [];
  }

  try {
    const summary = await dashboardService.getSummary();
    email.value = summary.email;
    name.value = summary.name;
  } catch {
    // interceptor apiClient sudah handle 401 (clear token + redirect /auth)
  }
});

// User bisa pindah project langsung dari topbar TANPA balik ke /dashboard
// dulu -- route param projectId berubah, watch ini yang trigger fetch ulang.
watch(projectId, (id) => {
  if (id) loadProjectDetail(id);
});

// ---- Guard role level project ----
// Route dengan meta.menu (lihat routes.ts) hanya boleh dibuka kalau menu itu
// ada di availableMenus dari backend -- mis. COLLABORATOR yang mengetik
// /projects/:id/settings langsung di address bar dilempar ke dashboard.
// (Proteksi SEBENARNYA tetap di backend: aksi OWNER-only dibalas 403.)
watch(
  [project, () => route.meta.menu],
  ([detail, menu]) => {
    if (!detail || typeof menu !== 'string') return;
    if (!detail.availableMenus.includes(menu as ProjectMenuCode)) {
      router.replace({ name: 'project-dashboard', params: { projectId: detail.id } });
    }
  },
  { immediate: true }
);

function switchProject(id: string) {
  isProjectSwitcherOpen.value = false;
  if (id === projectId.value) return;
  router.push({ name: 'project-dashboard', params: { projectId: id } });
}

function handleLogout() {
  tokenStorage.clearToken();
  router.push({ name: 'auth-signin' });
}
</script>

<template>
  <div class="flex min-h-screen flex-col bg-white">
    <!-- Notifikasi (toast) untuk seluruh halaman project -- lihat shared/composables/useToast.ts -->
    <ToastContainer />

    <!-- Top nav -->
    <header class="flex items-center justify-between border-b border-gray-100 px-8 py-4">
      <div class="flex items-center gap-3">
        <span class="flex items-center gap-2 text-lg font-bold text-gray-900">
          <BugIcon :size="22" />
          {{ BRAND_NAME }}
        </span>
        <span class="h-5 w-px bg-gray-200" />
        <RouterLink
          :to="{ name: 'dashboard' }"
          class="flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          <FolderIcon :size="16" />
          Projects
        </RouterLink>

        <!-- Project switcher -->
        <div v-if="project" class="relative">
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg bg-gray-100 px-3 py-1.5 text-sm font-semibold text-gray-900 hover:bg-gray-200"
            @click="isProjectSwitcherOpen = !isProjectSwitcherOpen"
          >
            {{ project.projectName }}
            <ChevronDownIcon :size="14" class="text-gray-500" />
          </button>

          <div
            v-if="isProjectSwitcherOpen"
            class="absolute left-0 z-10 mt-2 w-64 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
          >
            <button
              v-for="item in myProjects"
              :key="item.id"
              type="button"
              class="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
              @click="switchProject(item.id)"
            >
              <span class="truncate">{{ item.projectName }}</span>
              <CheckIcon v-if="item.id === projectId" :size="14" class="shrink-0 text-gray-900" />
            </button>
            <p v-if="myProjects.length === 0" class="px-4 py-2 text-sm text-gray-400">
              Tidak ada project lain
            </p>
          </div>
        </div>
      </div>

      <!-- User dropdown -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-3 rounded-lg px-2 py-1.5 hover:bg-gray-50"
          @click="isUserDropdownOpen = !isUserDropdownOpen"
        >
          <span
            class="flex h-9 w-9 items-center justify-center rounded-full bg-gray-900 text-sm font-semibold text-white"
          >
            {{ avatarInitial }}
          </span>
          <span class="text-left">
            <span class="block text-sm font-semibold text-gray-900">{{ displayName }}</span>
            <span class="block text-xs text-gray-400">{{ email }}</span>
          </span>
          <ChevronDownIcon :size="16" class="text-gray-400" />
        </button>

        <div
          v-if="isUserDropdownOpen"
          class="absolute right-0 z-10 mt-2 w-44 rounded-xl border border-gray-100 bg-white py-1.5 shadow-lg"
        >
          <button
            type="button"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
            @click="handleLogout"
          >
            <LogOutIcon :size="16" />
            Logout
          </button>
        </div>
      </div>
    </header>

    <!-- Body: sidebar + content -->
    <div v-if="project" class="flex flex-1">
      <ProjectSidebar :project-id="projectId" :available-menus="project.availableMenus" />
      <main class="min-w-0 flex-1">
        <RouterView />
      </main>
    </div>

    <div v-else-if="!isLoadingProject && loadError" class="flex min-h-[420px] flex-col items-center justify-center px-8 text-center">
      <p class="text-lg font-semibold text-gray-900">Project tidak bisa dibuka</p>
      <p class="mt-1 text-sm text-gray-400">{{ loadError }}</p>
      <RouterLink
        :to="{ name: 'dashboard' }"
        class="mt-6 rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
      >
        Kembali ke daftar project
      </RouterLink>
    </div>

    <div v-else class="flex flex-1 items-center justify-center text-sm text-gray-400">
      Memuat project...
    </div>
  </div>
</template>

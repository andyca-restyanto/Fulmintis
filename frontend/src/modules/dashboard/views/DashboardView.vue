<!-- frontend/src/modules/dashboard/views/DashboardView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { dashboardService } from '../services/dashboard.service';
import { tokenStorage } from '@/shared/services/tokenStorage';
import CreateProjectModal from '@/modules/project/components/CreateProjectModal.vue';
import ProjectCard from '@/modules/project/components/ProjectCard.vue';
import { projectService } from '@/modules/project/services/project.service';
import type { ProjectListItem, ProjectResponse } from '@/modules/project/types/project.types';
import BugIcon from '@/shared/components/icons/BugIcon.vue';
import { BRAND_NAME } from '@/shared/config/brand';
import FolderIcon from '@/shared/components/icons/FolderIcon.vue';
import PlusIcon from '@/shared/components/icons/PlusIcon.vue';
import ChevronDownIcon from '@/shared/components/icons/ChevronDownIcon.vue';
import LogOutIcon from '@/shared/components/icons/LogOutIcon.vue';

const router = useRouter();

const email = ref('');
const name = ref<string | null>(null); // nama custom user (nullable, lihat DashboardSummaryDTO.name)
const isLoading = ref(true);
const isDropdownOpen = ref(false);
const isCreateModalOpen = ref(false);

// Daftar project ASLI milik user (persist di database), diambil dari
// GET /api/projects (lihat ProjectController.listMyProjects di backend).
const projects = ref<ProjectListItem[]>([]);

// Pakai nama custom kalau user sudah set (Settings > Profile). Kalau belum,
// fallback derive dari bagian sebelum "@" dan "." di email,
// contoh: andyca.restyanto02@gmail.com -> "Andyca"
const displayName = computed(() => {
  const customName = name.value?.trim();
  if (customName) return customName;
  if (!email.value) return '';
  const localPart = email.value.split('@')[0] ?? '';
  const firstSegment = localPart.split('.')[0] ?? localPart;
  return firstSegment.charAt(0).toUpperCase() + firstSegment.slice(1);
});

const avatarInitial = computed(() => displayName.value.charAt(0) || '?');
const hasProjects = computed(() => projects.value.length > 0);

onMounted(async () => {
  try {
    const [summary, myProjects] = await Promise.all([
      dashboardService.getSummary(),
      projectService.listMyProjects(),
    ]);
    email.value = summary.email;
    name.value = summary.name;
    projects.value = myProjects;
  } finally {
    isLoading.value = false;
  }
  // Kalau request gagal (401 dsb), interceptor apiClient sudah otomatis
  // clear token & redirect ke /auth -> tidak perlu handle error manual di sini.
});

function handleLogout() {
  tokenStorage.clearToken();
  router.push({ name: 'auth-signin' });
}

function handleProjectCreated(created: ProjectResponse) {
  // Response POST /api/projects (ProjectResponseDTO) bentuknya sedikit beda
  // dgn GET /api/projects (ProjectListItemResponseDTO) -- map manual ke
  // shape ProjectListItem yang dipakai ProjectCard, tanpa perlu refetch list.
  const asListItem: ProjectListItem = {
    id: created.id,
    projectName: created.projectName,
    description: created.description,
    createdAt: created.createdAt,
    myProjectTeam: created.projectTeam,
  };
  projects.value.unshift(asListItem);
  isCreateModalOpen.value = false;
}
</script>

<template>
  <div class="min-h-screen bg-white">
    <!-- Top nav -->
    <header class="flex items-center justify-between border-b border-gray-100 px-8 py-4">
      <div class="flex items-center gap-3">
        <span class="flex items-center gap-2 text-lg font-bold text-gray-900">
          <BugIcon :size="22" />
          {{ BRAND_NAME }}
        </span>
        <span class="h-5 w-px bg-gray-200" />
        <span class="flex items-center gap-2 rounded-lg bg-gray-100 px-3 py-1.5 text-sm font-medium text-gray-700">
          <FolderIcon :size="16" />
          Projects
        </span>
      </div>

      <!-- User dropdown -->
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-3 rounded-lg px-2 py-1.5 hover:bg-gray-50"
          @click="isDropdownOpen = !isDropdownOpen"
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
          v-if="isDropdownOpen"
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

    <!-- Content -->
    <main class="px-8 py-8">
      <div class="mb-6 flex items-start justify-between">
        <div>
          <h1 class="flex items-center gap-2 text-2xl font-bold text-gray-900">
            Welcome back, {{ displayName }}
            <span>👋</span>
          </h1>
          <p class="mt-1 text-gray-500">Select a project to get started, or create a new one.</p>
        </div>
        <button
          type="button"
          class="flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
          @click="isCreateModalOpen = true"
        >
          <PlusIcon :size="16" />
          New Project
        </button>
      </div>

      <!-- Empty state -->
      <div
        v-if="!isLoading && !hasProjects"
        class="flex min-h-[420px] flex-col items-center justify-center rounded-2xl border-2 border-dashed border-gray-200"
      >
        <FolderIcon :size="40" class="mb-4 text-gray-300" />
        <p class="text-lg font-semibold text-gray-900">No projects yet</p>
        <p class="mt-1 text-sm text-gray-400">Create your first project to start managing test cases.</p>
        <button
          type="button"
          class="mt-6 flex items-center gap-2 rounded-xl bg-gray-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-gray-800"
          @click="isCreateModalOpen = true"
        >
          <PlusIcon :size="16" />
          Create Project
        </button>
      </div>

      <!-- Project grid -->
      <div v-else-if="hasProjects" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <ProjectCard v-for="project in projects" :key="project.id" :project="project" />
      </div>
    </main>

    <CreateProjectModal
      :open="isCreateModalOpen"
      @close="isCreateModalOpen = false"
      @created="handleProjectCreated"
    />
  </div>
</template>

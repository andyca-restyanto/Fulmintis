<!-- frontend/src/modules/project/views/ProjectMenuPlaceholderView.vue -->
<script setup lang="ts">
// Komponen generik utk menu yang belum punya modul backend sendiri --
// lihat routes.ts. Saat ini SEMUA menu (Test Repository, Test Runs,
// Reports, Settings) sudah punya view khusus, jadi tidak ada route yang
// memakai komponen ini lagi -- dibiarkan (bukan dihapus) utk dipakai ulang
// kalau menu baru ditambahkan sebelum modul backend-nya siap.
defineProps<{ menuLabel: string }>();
</script>

<template>
  <div class="flex min-h-[420px] flex-col items-center justify-center px-8 py-8 text-center">
    <p class="text-lg font-semibold text-gray-900">{{ menuLabel }}</p>
    <p class="mt-1 text-sm text-gray-400">Modul ini belum tersedia &mdash; coming soon.</p>
  </div>
</template>

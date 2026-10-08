// filepath: /frontend/src/modules/admin/utils/adminMenu.ts

/** Kunci ikon (komponen ikon dipetakan di AdminSidebar.vue; berkas ini sengaja bebas dari .vue agar bisa diuji). */
export type AdminMenuIconKey = 'shield' | 'users' | 'activity' | 'credit-card' | 'sparkles';

export interface AdminMenuEntry {
  routeName: string;
  label: string;
  icon: AdminMenuIconKey;
  /** Route lain yang tetap menyorot menu ini (mis. halaman undang admin = bagian dari menu Admin). */
  activeFor?: readonly string[];
}

// Urutan menu di sidebar: Admin PERTAMA, sebelum User. Menu selain Admin masih halaman "Coming soon".
export const ADMIN_MENU: readonly AdminMenuEntry[] = [
  { routeName: 'admin-admins', label: 'Admin', icon: 'shield', activeFor: ['admin-invite'] },
  { routeName: 'admin-users', label: 'User', icon: 'users' },
  { routeName: 'admin-user-logs', label: 'Log user', icon: 'activity' },
  { routeName: 'admin-payments', label: 'Payment', icon: 'credit-card' },
  { routeName: 'admin-ai-token-usage', label: 'AI token used', icon: 'sparkles' },
];

export function isMenuActive(entry: AdminMenuEntry, currentRouteName: string | symbol | null | undefined): boolean {
  if (typeof currentRouteName !== 'string') return false;
  return currentRouteName === entry.routeName || (entry.activeFor?.includes(currentRouteName) ?? false);
}

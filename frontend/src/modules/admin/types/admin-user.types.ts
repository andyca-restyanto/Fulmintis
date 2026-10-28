// filepath: /frontend/src/modules/admin/types/admin-user.types.ts

// Padanan DTO backend modules/admin/dto: UserListItemResponseDTO, UserListResponseDTO, UpdateUserTierRequestDTO.

/** Tier yang boleh dipilih admin. ADMIN sengaja tidak ada: itu bukan tier berlangganan. */
export type UserTier = 'FREE' | 'VIP_MONTHLY' | 'VIP_YEARLY';

export interface UserListItem {
  id: string;
  name: string | null;
  email: string;
  userType: UserTier;
  /** Label dari backend (mis. "VIP Monthly"). */
  userTypeLabel: string;
  verified: boolean;
  active: boolean;
  /** ISO LocalDateTime tanpa zona, mis. "2026-10-01T09:15:30". */
  createdAt: string;
}

export interface UserListPage {
  items: UserListItem[];
  /** Indeks halaman mulai dari 0 (nilai efektif yang dipakai backend). */
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface UpdateUserTierRequest {
  userType: UserTier;
}

/** Filter daftar user. String kosong = tidak difilter. */
export interface UserListFilters {
  q: string;
  tier: UserTier | '';
}

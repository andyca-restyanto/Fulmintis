// frontend/src/modules/project/types/project.types.ts

export interface CreateProjectRequest {
  projectName: string;
  description?: string;
}

export interface ProjectResponse {
  id: string;
  projectName: string;
  description: string | null;
  createdAt: string; // ISO date-time string
  ownerEmail: string;
  projectTeam: string; // selalu "OWNER" untuk project yang baru dibuat sendiri
}

// ---- Match 100% dgn ProjectListItemResponseDTO (backend) ----
// Response GET /api/projects -- daftar project milik user login.
export interface ProjectListItem {
  id: string;
  projectName: string;
  description: string | null;
  createdAt: string; // ISO date-time string
  myProjectTeam: string; // role user login di project ini: "OWNER" | "COLLABORATOR"
}

// ---- Match 100% dgn ProjectMenuCode (backend enum) ----
// Kode menu navigasi saat user masuk ke 1 project.
export type ProjectMenuCode =
  | 'DASHBOARD'
  | 'TEST_REPOSITORY'
  | 'TEST_RUNS'
  | 'REPORT'
  | 'AUTOMATION'
  | 'SETTING';

// ---- Match 100% dgn ProjectDetailResponseDTO (backend) ----
// Response GET /api/projects/{projectId} -- dipanggil saat user klik 1 project.
export interface ProjectDetail {
  id: string;
  projectName: string;
  description: string | null;
  createdAt: string; // ISO date-time string
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
  memberCount: number;
  myProjectTeam: string; // "OWNER" | "COLLABORATOR"
  availableMenus: ProjectMenuCode[];
}

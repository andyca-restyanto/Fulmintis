// frontend/src/modules/project/types/collaborator.types.ts

// Role yang BENERAN ada di sistem ini -- HANYA OWNER & COLLABORATOR (lihat
// ProjectTeamCode di backend). TIDAK ada role "Viewer".
export type ProjectTeamRole = 'OWNER' | 'COLLABORATOR';

// ---- Match 100% dgn AddProjectCollaboratorRequestDTO (backend) ----
export interface AddProjectCollaboratorRequest {
  email: string;
  projectTeam: ProjectTeamRole;
}

// ---- Match 100% dgn UpdateProjectCollaboratorRequestDTO (backend) ----
// PATCH /api/projects/{projectId}/collaborators/{collaborationId}
export interface UpdateProjectCollaboratorRequest {
  projectTeam: ProjectTeamRole;
}

// ---- Match 100% dgn ProjectCollaboratorResponseDTO (backend) ----
export interface ProjectCollaboratorResponse {
  id: number; // Long di backend (id project_collaboration), BUKAN UUID
  userId: string;
  email: string;
  name: string | null; // nullable -- requirement #4
  projectTeam: string; // 'OWNER' | 'COLLABORATOR'
  createdAt: string;
}

// ---- Match 100% dgn UserSearchResultDTO (backend) ----
export interface UserSearchResult {
  id: string;
  email: string;
  name: string | null;
}

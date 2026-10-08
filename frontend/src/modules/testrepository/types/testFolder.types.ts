// frontend/src/modules/testrepository/types/testFolder.types.ts

// ---- Match 100% dgn CreateTestFolderRequestDTO (backend) ----
export interface CreateTestFolderRequest {
  folderName: string;
  // Opsional -- null/undefined berarti folder jadi root (requirement #2).
  parentId?: string | null;
}

// ---- Match 100% dgn TestFolderResponseDTO (backend) ----
export interface TestFolderResponse {
  id: string;
  folderName: string;
  projectId: string;
  parentId: string | null; // null = folder root
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

// Bentuk KHUSUS FRONTEND (tidak ada di backend) -- hasil susun flat list
// TestFolderResponse[] jadi struktur tree bersarang, dipakai buat render
// sidebar "Test Suites" (lihat utils/testFolderTree.ts).
export interface TestFolderTreeNode extends TestFolderResponse {
  children: TestFolderTreeNode[];
}

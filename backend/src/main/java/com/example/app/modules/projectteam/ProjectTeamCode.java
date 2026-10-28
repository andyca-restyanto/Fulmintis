// backend/src/main/java/com/example/app/modules/projectteam/ProjectTeamCode.java
package com.example.app.modules.projectteam;

/**
 * Nilai-nilai di kolom project_team.description (sekaligus dipakai sebagai
 * "kode" yang disimpan di project_collaboration.project_team -- lihat
 * catatan cross-database di ProjectCollaboration.java).
 */
public final class ProjectTeamCode {
    private ProjectTeamCode() {
    }

    public static final String OWNER = "OWNER";
    public static final String COLLABORATOR = "COLLABORATOR";
}

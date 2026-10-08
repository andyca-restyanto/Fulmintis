// backend/src/main/java/com/example/app/modules/project/exception/ProjectNotFoundException.java
package com.example.app.modules.project.exception;

/**
 * Dilempar baik saat project_id benar-benar tidak ada di database, MAUPUN
 * saat project-nya ada tapi user yang request BUKAN member-nya (tidak ada
 * baris di project_collaboration untuk pasangan project_id+user_id itu).
 *
 * SENGAJA disamakan jadi 404 untuk kedua kasus itu (bukan 403 utk kasus
 * kedua) -- supaya user A tidak bisa pakai response code utk nebak-nebak
 * "project ini ada tapi saya bukan member" vs "project ini memang tidak
 * ada". Pola anti-enumeration ini sama dengan forgotPassword() di
 * AuthServiceImpl.
 */
public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException() {
        super("Project tidak ditemukan.");
    }
}

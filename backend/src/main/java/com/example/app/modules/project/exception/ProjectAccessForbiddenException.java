// backend/src/main/java/com/example/app/modules/project/exception/ProjectAccessForbiddenException.java
package com.example.app.modules.project.exception;

/**
 * Dilempar saat user SUDAH TERBUKTI member project (beda dengan
 * ProjectNotFoundException) tapi role-nya di project ini tidak cukup untuk
 * aksi yang dia coba lakukan, misal: COLLABORATOR coba create test folder
 * padahal aksi itu cuma boleh OWNER (requirement #3).
 * <p>
 * SENGAJA dibalas 403 (bukan 404 seperti ProjectNotFoundException) karena
 * di titik ini user memang sudah terbukti sah sebagai member project --
 * tidak ada risiko enumeration, cuma kurang izin.
 */
public class ProjectAccessForbiddenException extends RuntimeException {
    public ProjectAccessForbiddenException(String message) {
        super(message);
    }
}

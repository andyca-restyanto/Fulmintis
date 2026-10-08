// filepath: /backend/src/main/java/com/example/app/modules/project/exception/CollaborationNotFoundException.java
package com.example.app.modules.project.exception;

/**
 * Dilempar saat collaborationId (baris project_collaboration) yang mau
 * diubah/dihapus tidak ada di project ini. Dipetakan ke 404.
 */
public class CollaborationNotFoundException extends RuntimeException {
    public CollaborationNotFoundException() {
        super("Team member tidak ditemukan di project ini.");
    }
}

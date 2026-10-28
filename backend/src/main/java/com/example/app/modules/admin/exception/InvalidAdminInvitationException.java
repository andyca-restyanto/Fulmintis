// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/InvalidAdminInvitationException.java
package com.example.app.modules.admin.exception;

public class InvalidAdminInvitationException extends RuntimeException {
    public InvalidAdminInvitationException() {
        // Pesan generik sengaja: tidak membocorkan detail (mis. nilai/panjang kode yang benar).
        super("Link undangan tidak valid atau sudah kedaluwarsa");
    }
}

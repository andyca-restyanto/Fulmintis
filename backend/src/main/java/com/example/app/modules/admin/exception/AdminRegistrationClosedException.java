// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/AdminRegistrationClosedException.java
package com.example.app.modules.admin.exception;

public class AdminRegistrationClosedException extends RuntimeException {
    public AdminRegistrationClosedException() {
        // Pesan generik sengaja: tidak membocorkan detail (mis. nilai/panjang kode yang benar).
        super("Pendaftaran admin sudah ditutup");
    }
}

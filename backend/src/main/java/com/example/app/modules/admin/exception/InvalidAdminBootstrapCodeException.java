// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/InvalidAdminBootstrapCodeException.java
package com.example.app.modules.admin.exception;

public class InvalidAdminBootstrapCodeException extends RuntimeException {
    public InvalidAdminBootstrapCodeException() {
        // Pesan generik sengaja: tidak membocorkan detail (mis. nilai/panjang kode yang benar).
        super("Kode tidak valid");
    }
}

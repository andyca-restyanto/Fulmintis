// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/AdminNotFoundException.java
package com.example.app.modules.admin.exception;

/** Id tidak ada atau bukan akun ADMIN -> 404 (akun user biasa tidak dibedakan). */
public class AdminNotFoundException extends RuntimeException {
    public AdminNotFoundException() {
        super("Admin tidak ditemukan.");
    }
}

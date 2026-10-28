// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/UserNotFoundException.java
package com.example.app.modules.admin.exception;

/** Id tidak ada atau akun ADMIN -> 404 (akun admin tidak dibedakan dari "tidak ada" di menu User). */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User tidak ditemukan.");
    }
}

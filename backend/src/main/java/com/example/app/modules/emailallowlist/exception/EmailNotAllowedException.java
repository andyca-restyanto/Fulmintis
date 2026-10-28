// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/exception/EmailNotAllowedException.java
package com.example.app.modules.emailallowlist.exception;

/** Register ditolak karena email tidak ada di daftar izin. Pesan sengaja umum (tidak membocorkan isi daftar). */
public class EmailNotAllowedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_ALLOWED";

    public EmailNotAllowedException() {
        super("Email ini tidak diizinkan untuk mendaftar. Hubungi admin.");
    }
}

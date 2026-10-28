// backend/src/main/java/com/example/app/modules/auth/exception/InvalidCredentialsException.java
package com.example.app.modules.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        // Sengaja pesan generik (tidak bilang "email tidak ditemukan" atau
        // "password salah" secara spesifik) supaya tidak bocorkan info mana
        // yang salah -> mencegah user enumeration attack.
        super("Email atau password salah");
    }
}

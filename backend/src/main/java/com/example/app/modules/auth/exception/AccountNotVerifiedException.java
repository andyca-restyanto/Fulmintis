// backend/src/main/java/com/example/app/modules/auth/exception/AccountNotVerifiedException.java
package com.example.app.modules.auth.exception;

public class AccountNotVerifiedException extends RuntimeException {
    public AccountNotVerifiedException() {
        super("Akun belum diverifikasi. Silakan cek email kamu untuk melakukan verifikasi terlebih dahulu.");
    }
}

// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/AccountDeactivatedException.java
package com.example.app.modules.admin.exception;

/**
 * Login admin dengan password benar tetapi akun dinonaktifkan -> 403 dengan {@code code}.
 * Hanya dilempar SETELAH password terbukti benar, jadi tidak membuka enumerasi akun.
 * {@link #CODE} membedakannya dari 403 "belum verifikasi" (FE menampilkan form kirim ulang
 * verifikasi hanya untuk yang terakhir).
 */
public class AccountDeactivatedException extends RuntimeException {

    public static final String CODE = "ACCOUNT_DEACTIVATED";

    public AccountDeactivatedException() {
        super("Akun admin ini dinonaktifkan. Hubungi admin lain untuk mengaktifkannya kembali.");
    }
}

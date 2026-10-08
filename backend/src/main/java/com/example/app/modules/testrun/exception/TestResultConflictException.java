// filepath: /backend/src/main/java/com/example/app/modules/testrun/exception/TestResultConflictException.java
package com.example.app.modules.testrun.exception;

/**
 * Dilempar saat klien mengirim {@code version} test result yang sudah usang
 * (orang lain menyimpan lebih dulu). Dipetakan ke HTTP 409 oleh
 * GlobalExceptionHandler -- FE sebaiknya memuat ulang data lalu menawarkan
 * user untuk mengulang perubahannya.
 */
public class TestResultConflictException extends RuntimeException {
    public TestResultConflictException() {
        super("Hasil test ini sudah diubah pengguna lain. Muat ulang halaman lalu coba lagi.");
    }
}

// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/LastActiveAdminException.java
package com.example.app.modules.admin.exception;

/** Penonaktifan akan menyisakan nol admin aktif -> 409. */
public class LastActiveAdminException extends RuntimeException {
    public LastActiveAdminException() {
        super("Admin aktif terakhir tidak dapat dinonaktifkan.");
    }
}

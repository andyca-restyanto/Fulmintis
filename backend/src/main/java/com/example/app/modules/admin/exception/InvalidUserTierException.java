// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/InvalidUserTierException.java
package com.example.app.modules.admin.exception;

/** Tier bukan salah satu FREE / VIP_MONTHLY / VIP_YEARLY (termasuk ADMIN) -> 400. */
public class InvalidUserTierException extends RuntimeException {
    public InvalidUserTierException() {
        super("Tier tidak valid. Pilih FREE, VIP_MONTHLY, atau VIP_YEARLY.");
    }
}

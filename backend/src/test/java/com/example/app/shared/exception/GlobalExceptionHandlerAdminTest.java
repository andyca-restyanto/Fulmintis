// filepath: /backend/src/test/java/com/example/app/shared/exception/GlobalExceptionHandlerAdminTest.java
package com.example.app.shared.exception;

import com.example.app.modules.admin.exception.AccountDeactivatedException;
import com.example.app.modules.admin.exception.AdminNotFoundException;
import com.example.app.modules.admin.exception.CannotDeactivateSelfException;
import com.example.app.modules.admin.exception.InvalidUserQueryException;
import com.example.app.modules.admin.exception.InvalidUserTierException;
import com.example.app.modules.admin.exception.LastActiveAdminException;
import com.example.app.modules.admin.exception.UserNotFoundException;
import com.example.app.modules.auth.exception.AccountNotVerifiedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Status HTTP dan bentuk body error untuk menu Admin (kontrak dengan FE). */
class GlobalExceptionHandlerAdminTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertBody(ResponseEntity<Map<String, Object>> response, int status, String message) {
        assertEquals(status, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.get("status"));
        assertEquals(message, body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void deactivatedAccountIs403WithMachineReadableCode() {
        ResponseEntity<Map<String, Object>> response = handler.handleAccountDeactivated(new AccountDeactivatedException());

        assertBody(response, 403, new AccountDeactivatedException().getMessage());
        assertEquals("ACCOUNT_DEACTIVATED", response.getBody().get("code"));
    }

    @Test
    void notVerifiedStays403WithoutCodeSoFrontendCanTellThemApart() {
        ResponseEntity<Map<String, Object>> response = handler.handleAccountNotVerified(new AccountNotVerifiedException());

        assertEquals(403, response.getStatusCode().value());
        assertFalse(response.getBody().containsKey("code"));
    }

    @Test
    void adminManagementErrorsMapToTheirStatusesWithoutCode() {
        ResponseEntity<Map<String, Object>> self = handler.handleCannotDeactivateSelf(new CannotDeactivateSelfException());
        ResponseEntity<Map<String, Object>> last = handler.handleLastActiveAdmin(new LastActiveAdminException());
        ResponseEntity<Map<String, Object>> missing = handler.handleAdminNotFound(new AdminNotFoundException());

        assertBody(self, 400, "Anda tidak dapat menonaktifkan akun Anda sendiri.");
        assertBody(last, 409, "Admin aktif terakhir tidak dapat dinonaktifkan.");
        assertBody(missing, 404, "Admin tidak ditemukan.");
        for (ResponseEntity<Map<String, Object>> r : java.util.List.of(self, last, missing)) {
            assertFalse(r.getBody().containsKey("code"));
        }
    }

    @Test
    void userManagementErrorsMapToTheirStatuses() {
        assertBody(handler.handleUserNotFound(new UserNotFoundException()), 404, "User tidak ditemukan.");
        assertBody(handler.handleInvalidUserTier(new InvalidUserTierException()), 400,
                "Tier tidak valid. Pilih FREE, VIP_MONTHLY, atau VIP_YEARLY.");
        assertBody(handler.handleInvalidUserQuery(new InvalidUserQueryException(100)), 400,
                "Kata kunci pencarian maksimal 100 karakter.");
    }
}

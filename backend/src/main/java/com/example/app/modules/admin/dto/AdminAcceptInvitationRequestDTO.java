// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminAcceptInvitationRequestDTO.java
package com.example.app.modules.admin.dto;

import com.example.app.modules.admin.validation.PasswordConfirmation;
import com.example.app.modules.admin.validation.PasswordConfirmationMatches;
import com.example.app.modules.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Penerima undangan membuat password. Match: AdminAcceptInvitationRequest (FE). */
@Getter
@Setter
@PasswordConfirmationMatches
public class AdminAcceptInvitationRequestDTO implements PasswordConfirmation {

    @NotBlank(message = "Token wajib diisi")
    private String token;

    @NotBlank(message = "Password baru wajib diisi")
    @ValidPassword
    private String newPassword;

    @NotBlank(message = "Konfirmasi password wajib diisi")
    private String confirmPassword;

    @Override
    public String enteredPassword() {
        return newPassword;
    }

    @Override
    public String enteredConfirmation() {
        return confirmPassword;
    }
}

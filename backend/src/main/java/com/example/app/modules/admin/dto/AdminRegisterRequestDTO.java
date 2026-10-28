// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminRegisterRequestDTO.java
package com.example.app.modules.admin.dto;

import com.example.app.modules.admin.validation.PasswordConfirmation;
import com.example.app.modules.admin.validation.PasswordConfirmationMatches;
import com.example.app.modules.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Pendaftaran ADMIN PERTAMA. Match dengan frontend: modules/admin/types/admin-auth.types.ts (AdminRegisterRequest). */
@Getter
@Setter
@PasswordConfirmationMatches
public class AdminRegisterRequestDTO implements PasswordConfirmation {

    @NotBlank(message = "Nama wajib diisi")
    @Size(max = 255, message = "Nama maksimal 255 karakter")
    private String name;

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    private String email;

    @NotBlank(message = "Password wajib diisi")
    @ValidPassword
    private String password;

    @NotBlank(message = "Confirm password wajib diisi")
    private String confirmPassword;

    /** Opsional: wajib benar hanya kalau app.admin.bootstrap-code dikonfigurasi. */
    @Size(max = 255, message = "Kode maksimal 255 karakter")
    private String bootstrapCode;

    @Override
    public String enteredPassword() {
        return password;
    }

    @Override
    public String enteredConfirmation() {
        return confirmPassword;
    }
}

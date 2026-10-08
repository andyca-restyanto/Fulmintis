// backend/src/main/java/com/example/app/modules/auth/dto/ChangePasswordRequestDTO.java
package com.example.app.modules.auth.dto;

import com.example.app.modules.auth.validation.ChangePasswordMatches;
import com.example.app.modules.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ChangePasswordMatches
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Password saat ini wajib diisi")
    private String currentPassword;

    @NotBlank(message = "Password baru wajib diisi")
    @ValidPassword
    private String newPassword;

    @NotBlank(message = "Konfirmasi password baru wajib diisi")
    private String confirmNewPassword;
}

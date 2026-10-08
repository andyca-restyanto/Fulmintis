// backend/src/main/java/com/example/app/modules/auth/dto/ResetPasswordRequestDTO.java
package com.example.app.modules.auth.dto;

import com.example.app.modules.auth.validation.NewPasswordMatches;
import com.example.app.modules.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NewPasswordMatches
public class ResetPasswordRequestDTO {

    @NotBlank(message = "Token wajib diisi")
    private String token;

    @NotBlank(message = "Password baru wajib diisi")
    @ValidPassword
    private String newPassword;

    @NotBlank(message = "Konfirmasi password baru wajib diisi")
    private String confirmNewPassword;
}

// backend/src/main/java/com/example/app/modules/auth/dto/RegisterRequestDTO.java
package com.example.app.modules.auth.dto;

import com.example.app.modules.auth.validation.PasswordMatches;
import com.example.app.modules.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@PasswordMatches // validasi cross-field: password == confirmPassword
public class RegisterRequestDTO {

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    private String email;

    @NotBlank(message = "Password wajib diisi")
    @ValidPassword
    private String password;

    @NotBlank(message = "Confirm password wajib diisi")
    private String confirmPassword;
}

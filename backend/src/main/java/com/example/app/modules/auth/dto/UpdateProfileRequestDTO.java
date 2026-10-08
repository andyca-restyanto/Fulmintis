// backend/src/main/java/com/example/app/modules/auth/dto/UpdateProfileRequestDTO.java
package com.example.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequestDTO {

    @NotBlank(message = "Nama wajib diisi")
    @Size(max = 255, message = "Nama maksimal 255 karakter")
    private String name;
}

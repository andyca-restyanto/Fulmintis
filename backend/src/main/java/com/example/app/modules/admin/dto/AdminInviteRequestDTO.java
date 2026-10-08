// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminInviteRequestDTO.java
package com.example.app.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Admin yang sudah login mengundang admin baru. Match: AdminInviteRequest (FE). */
@Getter
@Setter
public class AdminInviteRequestDTO {

    @NotBlank(message = "Nama wajib diisi")
    @Size(max = 255, message = "Nama maksimal 255 karakter")
    private String name;

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    private String email;
}

// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/UpdateUserTierRequestDTO.java
package com.example.app.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Body PATCH /api/admin/users/{id}/tier. Match: UpdateUserTierRequest (FE). */
@Getter
@Setter
public class UpdateUserTierRequestDTO {

    /** FREE | VIP_MONTHLY | VIP_YEARLY (nilai lain, termasuk ADMIN, ditolak 400 oleh service). */
    @NotBlank(message = "Tier wajib diisi")
    private String userType;
}

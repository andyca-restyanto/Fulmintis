// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/dto/AllowlistSettingsRequestDTO.java
package com.example.app.modules.emailallowlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body PUT /api/admin/email-allowlist/settings. Match: AllowlistSettingsRequest (FE). */
@Getter
@Setter
public class AllowlistSettingsRequestDTO {

    @NotNull(message = "Status wajib diisi")
    private Boolean enabled;
}

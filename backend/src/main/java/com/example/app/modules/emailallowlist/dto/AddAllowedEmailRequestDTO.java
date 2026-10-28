// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/dto/AddAllowedEmailRequestDTO.java
package com.example.app.modules.emailallowlist.dto;

import com.example.app.modules.emailallowlist.entity.AllowedEmailRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body POST /api/admin/email-allowlist. Match: AddAllowedEmailRequest (FE). Format value dicek service. */
@Getter
@Setter
public class AddAllowedEmailRequestDTO {

    @NotNull(message = "Jenis aturan wajib diisi")
    private AllowedEmailRuleType ruleType;

    @NotBlank(message = "Nilai wajib diisi")
    @Size(max = 255, message = "Nilai maksimal 255 karakter")
    private String value;
}

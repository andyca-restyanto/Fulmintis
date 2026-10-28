// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/dto/AllowedEmailRuleDTO.java
package com.example.app.modules.emailallowlist.dto;

import com.example.app.modules.emailallowlist.entity.AllowedEmailRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Match: AllowedEmailRule (FE, admin-email-allowlist.types.ts). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedEmailRuleDTO {
    private Long id;
    private AllowedEmailRuleType ruleType;
    private String value;
    private LocalDateTime createdAt;
    private String createdBy;
}

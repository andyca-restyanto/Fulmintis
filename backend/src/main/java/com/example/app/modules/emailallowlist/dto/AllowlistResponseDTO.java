// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/dto/AllowlistResponseDTO.java
package com.example.app.modules.emailallowlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** Match: AllowlistResponse (FE). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowlistResponseDTO {
    private boolean enabled;
    private List<AllowedEmailRuleDTO> rules;
}

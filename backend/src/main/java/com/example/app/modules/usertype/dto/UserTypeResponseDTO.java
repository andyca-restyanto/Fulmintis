// backend/src/main/java/com/example/app/modules/usertype/dto/UserTypeResponseDTO.java
package com.example.app.modules.usertype.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserTypeResponseDTO {
    private Long id;
    private String code;
    private String label;
}

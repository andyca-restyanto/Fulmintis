// backend/src/main/java/com/example/app/modules/project/dto/UserSearchResultDTO.java
package com.example.app.modules.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UserSearchResultDTO {
    private UUID id;
    private String email;
    private String name; // nullable
}

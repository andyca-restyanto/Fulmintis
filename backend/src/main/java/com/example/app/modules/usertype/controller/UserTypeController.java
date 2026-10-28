// backend/src/main/java/com/example/app/modules/usertype/controller/UserTypeController.java
package com.example.app.modules.usertype.controller;

import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.dto.UserTypeResponseDTO;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user-types")
@RequiredArgsConstructor
public class UserTypeController {

    private final UserTypeRepository userTypeRepository;

    /**
     * Berguna untuk populate dropdown di frontend (misal halaman upgrade akun).
     * Tipe ADMIN sengaja TIDAK disertakan: itu bukan pilihan yang boleh dilihat/dipilih user.
     */
    @GetMapping
    public List<UserTypeResponseDTO> getAll() {
        return userTypeRepository.findAllByCodeNotOrderBySortOrderAsc(UserTypeCode.ADMIN).stream()
                .map(this::toDto)
                .toList();
    }

    private UserTypeResponseDTO toDto(UserType entity) {
        return UserTypeResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .label(entity.getLabel())
                .build();
    }
}

// filepath: /backend/src/test/java/com/example/app/modules/usertype/UserTypeAdminTest.java
package com.example.app.modules.usertype;

import com.example.app.modules.usertype.controller.UserTypeController;
import com.example.app.modules.usertype.dto.UserTypeResponseDTO;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.modules.usertype.seed.UserTypeSeeder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTypeAdminTest {

    @Test
    void seederAddsAdminTypeWithSortOrder4() {
        UserTypeRepository repo = mock(UserTypeRepository.class);
        when(repo.existsByCode(anyString())).thenReturn(false);

        new UserTypeSeeder(repo).run();

        ArgumentCaptor<UserType> saved = ArgumentCaptor.forClass(UserType.class);
        verify(repo, atLeastOnce()).save(saved.capture());
        UserType admin = saved.getAllValues().stream()
                .filter(t -> UserTypeCode.ADMIN.equals(t.getCode())).findFirst().orElseThrow();
        assertEquals("Admin", admin.getLabel());
        assertEquals(4, admin.getSortOrder());
    }

    @Test
    void seederIsIdempotentForExistingAdminType() {
        UserTypeRepository repo = mock(UserTypeRepository.class);
        when(repo.existsByCode(anyString())).thenReturn(true);

        new UserTypeSeeder(repo).run();

        verify(repo, never()).save(any(UserType.class));
    }

    @Test
    void publicUserTypeListExcludesAdmin() {
        UserTypeRepository repo = mock(UserTypeRepository.class);
        when(repo.findAllByCodeNotOrderBySortOrderAsc(UserTypeCode.ADMIN)).thenReturn(List.of(
                UserType.builder().id(1L).code(UserTypeCode.FREE).label("Free").build(),
                UserType.builder().id(2L).code(UserTypeCode.VIP_MONTHLY).label("VIP Monthly").build()));

        List<UserTypeResponseDTO> result = new UserTypeController(repo).getAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(t -> UserTypeCode.ADMIN.equals(t.getCode())));
        verify(repo, never()).findAllByOrderBySortOrderAsc();
    }
}

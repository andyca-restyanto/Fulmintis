// filepath: /backend/src/test/java/com/example/app/modules/admin/UserDtoJsonAndValidationTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.UpdateUserTierRequestDTO;
import com.example.app.modules.admin.dto.UserListItemResponseDTO;
import com.example.app.modules.admin.dto.UserListResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mengunci bentuk JSON yang dipakai FE (UserListPage / UserListItem / UpdateUserTierRequest):
 * kalau nama field berubah, test ini gagal -- bukan FE yang diam-diam menampilkan kolom kosong.
 */
class UserDtoJsonAndValidationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void pageEnvelopeAndItemUseTheAgreedFieldNames() throws Exception {
        UserListItemResponseDTO item = UserListItemResponseDTO.builder()
                .id(UUID.fromString("0b6f9a52-3c1e-4f0a-9d57-6f1c2a8e4b11"))
                .name("Budi Santoso").email("budi@example.com")
                .userType("VIP_MONTHLY").userTypeLabel("VIP Monthly")
                .verified(true).active(true)
                .createdAt(LocalDateTime.of(2026, 10, 1, 9, 15, 30))
                .build();
        UserListResponseDTO page = UserListResponseDTO.builder()
                .items(List.of(item)).page(0).size(10).totalItems(23).totalPages(3).build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(page));

        assertEquals(Set.of("items", "page", "size", "totalItems", "totalPages"), fieldNames(json));
        JsonNode row = json.get("items").get(0);
        assertEquals(
                Set.of("id", "name", "email", "userType", "userTypeLabel", "verified", "active", "createdAt"),
                fieldNames(row));
        assertEquals("VIP_MONTHLY", row.get("userType").asText());
        assertEquals("VIP Monthly", row.get("userTypeLabel").asText());
        assertTrue(row.get("verified").isBoolean());
        assertTrue(row.get("active").isBoolean());
        assertEquals("2026-10-01T09:15:30", row.get("createdAt").asText());
    }

    @Test
    void missingNameIsExplicitNullNotOmitted() throws Exception {
        UserListItemResponseDTO item = UserListItemResponseDTO.builder()
                .id(UUID.randomUUID()).email("x@example.com").userType("FREE").userTypeLabel("Free")
                .verified(false).active(true).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();

        JsonNode row = mapper.readTree(mapper.writeValueAsString(item));

        assertTrue(row.has("name"));
        assertTrue(row.get("name").isNull());
    }

    @Test
    void tierRequestReadsUserTypeFromJsonAndRequiresIt() throws Exception {
        UpdateUserTierRequestDTO ok = mapper.readValue("{\"userType\":\"VIP_YEARLY\"}", UpdateUserTierRequestDTO.class);
        assertEquals("VIP_YEARLY", ok.getUserType());
        assertTrue(validator.validate(ok).isEmpty());

        UpdateUserTierRequestDTO missing = mapper.readValue("{}", UpdateUserTierRequestDTO.class);
        assertFalse(validator.validate(missing).isEmpty());

        UpdateUserTierRequestDTO blank = mapper.readValue("{\"userType\":\"  \"}", UpdateUserTierRequestDTO.class);
        assertFalse(validator.validate(blank).isEmpty());
    }
}

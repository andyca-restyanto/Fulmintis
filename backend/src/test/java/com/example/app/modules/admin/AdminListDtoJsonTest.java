// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminListDtoJsonTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AdminListItemResponseDTO;
import com.example.app.modules.admin.dto.AdminListResponseDTO;
import com.example.app.modules.admin.dto.AdminStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mengunci bentuk JSON yang dipakai FE (AdminListPage / AdminListItem). Kalau nama field berubah,
 * test ini gagal -- bukan FE yang diam-diam menampilkan kolom kosong.
 */
class AdminListDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void pageEnvelopeAndItemUseTheAgreedFieldNames() throws Exception {
        AdminListItemResponseDTO item = AdminListItemResponseDTO.builder()
                .id(UUID.fromString("a2d94e77-1c6b-4f0a-8e35-7d1b9c4e2a60"))
                .name("Sari Admin")
                .email("sari@contoh.com")
                .status(AdminStatus.PENDING)
                .self(false)
                .createdAt(LocalDateTime.of(2026, 10, 8, 11, 0))
                .invitationExpiresAt(LocalDateTime.of(2026, 10, 9, 11, 0))
                .build();
        AdminListResponseDTO page = AdminListResponseDTO.builder()
                .items(List.of(item)).page(0).size(10).totalItems(23).totalPages(3).build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(page));

        assertEquals(Set.of("items", "page", "size", "totalItems", "totalPages"), fieldNames(json));
        assertEquals(23, json.get("totalItems").asLong());
        JsonNode row = json.get("items").get(0);
        assertEquals(
                Set.of("id", "name", "email", "status", "self", "createdAt", "invitationExpiresAt"),
                fieldNames(row));
        assertEquals("PENDING", row.get("status").asText());          // enum sebagai string literal
        assertTrue(row.get("self").isBoolean());
        assertEquals("2026-10-09T11:00:00", row.get("invitationExpiresAt").asText());
    }

    @Test
    void invitationExpiryIsExplicitNullNotOmitted() throws Exception {
        AdminListItemResponseDTO item = AdminListItemResponseDTO.builder()
                .id(UUID.randomUUID()).name("Budi").email("budi@contoh.com")
                .status(AdminStatus.ACTIVE).self(true)
                .createdAt(LocalDateTime.of(2026, 10, 8, 9, 0))
                .build();

        JsonNode row = mapper.readTree(mapper.writeValueAsString(item));

        assertTrue(row.has("invitationExpiresAt"));
        assertTrue(row.get("invitationExpiresAt").isNull());
    }

    @Test
    void everyStatusSerializesToItsLiteralName() throws Exception {
        List<String> names = new ArrayList<>();
        for (AdminStatus s : AdminStatus.values()) {
            names.add(mapper.readTree(mapper.writeValueAsString(s)).asText());
        }
        assertEquals(List.of("ACTIVE", "PENDING", "INACTIVE"), names);
    }
}

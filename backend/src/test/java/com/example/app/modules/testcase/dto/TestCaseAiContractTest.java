// filepath: /backend/src/test/java/com/example/app/modules/testcase/dto/TestCaseAiContractTest.java
package com.example.app.modules.testcase.dto;

import com.example.app.modules.testcase.TestCaseAiGenerationStatus;
import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.service.TestCaseAiErrors;
import com.example.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menjaga kontrak JSON dgn frontend (nama field persis): perubahan nama/penghapusan field membuat test ini gagal, bukan membuat
 * layar review frontend rusak diam-diam. Memakai pengaturan ObjectMapper yang sama dgn Spring Boot (tanggal sbg teks ISO).
 */
class TestCaseAiContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private java.util.Set<String> keys(Object value) {
        JsonNode node = mapper.valueToTree(value);
        java.util.Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static java.util.Set<String> set(String... names) {
        return new TreeSet<>(List.of(names));
    }

    @Test
    void usageHasExactlyTheDocumentedFields() {
        TestCaseAiUsageResponseDTO usage = TestCaseAiUsageResponseDTO.builder().aiEnabled(true).tier("VIP")
                .maxDraftsPerGeneration(15).maxRequirementChars(6000).dailyLimit(15).usedToday(2).sandbox(false).build();

        assertEquals(set("aiEnabled", "tier", "maxDraftsPerGeneration", "maxRequirementChars", "dailyLimit", "usedToday", "sandbox"), keys(usage));
        assertEquals(15, mapper.valueToTree(usage).get("dailyLimit").asInt());
    }

    @Test
    void createdAndCommitResponsesHaveTheDocumentedFields() {
        UUID id = UUID.randomUUID();
        assertEquals(set("id", "status"), keys(TestCaseAiCreatedDTO.builder().id(id).status(TestCaseAiGenerationStatus.QUEUED).build()));
        assertEquals("QUEUED", mapper.valueToTree(TestCaseAiCreatedDTO.builder().id(id).status(TestCaseAiGenerationStatus.QUEUED).build()).get("status").asText());
        assertEquals(set("savedCount", "folderId", "folderName"),
                keys(TestCaseAiCommitResponseDTO.builder().savedCount(2).folderId(id).folderName("f").build()));
    }

    @Test
    void generationDetailAndDraftsHaveTheDocumentedFieldsAndFormats() {
        TestCaseDraftDTO draft = new TestCaseDraftDTO("d1", "Judul", TestCasePriority.HIGH, TestCaseType.MANUAL, TestCaseScenarioType.POSITIVE,
                "desc", "obj", "pre", List.of(new TestCaseDraftStepDTO("buka", "tampil")), true);
        TestCaseAiGenerationResponseDTO detail = TestCaseAiGenerationResponseDTO.builder().id(UUID.randomUUID())
                .status(TestCaseAiGenerationStatus.SUCCEEDED).folderId(UUID.randomUUID()).folderName("folder 1").requestedCount(15)
                .draftCount(12).truncated(true).committed(false).createdAt(LocalDateTime.of(2026, 10, 5, 10, 0, 0))
                .finishedAt(LocalDateTime.of(2026, 10, 5, 10, 0, 7)).drafts(List.of(draft)).build();

        JsonNode json = mapper.valueToTree(detail);

        assertEquals(set("id", "status", "folderId", "folderName", "requestedCount", "draftCount", "truncated", "committed",
                "createdAt", "finishedAt", "drafts", "errorCode", "errorMessage"), keys(detail));
        assertEquals(set("tempId", "title", "priority", "type", "scenarioType", "description", "objective", "precondition", "steps",
                "duplicateOfExisting"), keys(draft));
        assertEquals(set("action", "expected"), keys(draft.steps().get(0)));
        assertEquals("2026-10-05T10:00:00", json.get("createdAt").asText(), "tanggal sbg teks ISO (bukan array angka)");
        assertEquals("HIGH", json.at("/drafts/0/priority").asText());
        assertEquals("POSITIVE", json.at("/drafts/0/scenarioType").asText());
        assertEquals(true, json.at("/drafts/0/duplicateOfExisting").asBoolean());
        assertTrue(json.get("errorCode").isNull());
    }

    @Test
    void theCommitRequestFromTheDocumentedExampleDeserialisesIntoTheDto() throws Exception {
        String body = "{\"folderId\":\"" + UUID.randomUUID() + "\",\"testCases\":[{\"title\":\"Judul\",\"priority\":\"HIGH\",\"type\":\"MANUAL\","
                + "\"scenarioType\":\"POSITIVE\",\"description\":\"d\",\"objective\":\"o\",\"precondition\":\"p\","
                + "\"testStep\":\"1. a\\n2. b\",\"expectedResult\":\"1. x\\n2. y\"}]}";

        TestCaseAiCommitRequestDTO request = mapper.readValue(body, TestCaseAiCommitRequestDTO.class);

        TestCaseAiCommitItemDTO item = request.getTestCases().get(0);
        assertEquals("Judul", item.getTitle());
        assertEquals(TestCasePriority.HIGH, item.getPriority());
        assertEquals(TestCaseScenarioType.POSITIVE, item.getScenarioType());
        assertEquals("1. a\n2. b", item.getTestStep());
    }

    @Test
    void theGenerateRequestFromTheDocumentedExampleDeserialises() throws Exception {
        String body = "{\"folderId\":\"" + UUID.randomUUID() + "\",\"requirement\":\"teks\",\"count\":3,\"includeNegative\":false}";

        TestCaseAiGenerateRequestDTO request = mapper.readValue(body, TestCaseAiGenerateRequestDTO.class);

        assertEquals("teks", request.getRequirement());
        assertEquals(Integer.valueOf(3), request.getCount());
        assertEquals(Boolean.FALSE, request.getIncludeNegative());
    }

    @Test
    void theErrorBodiesCarryErrorCodeAndPerItemErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        List<Map<String, Object>> errors = new ArrayList<>();
        errors.add(Map.of("index", 1, "field", "title", "message", "Title wajib diisi"));

        Map<String, Object> invalid = handler.handleAiApi(TestCaseAiErrors.invalidDrafts(errors)).getBody();
        assertEquals(set("timestamp", "status", "message", "errorCode", "errors"), new TreeSet<>(invalid.keySet()));
        assertEquals(400, invalid.get("status"));
        assertEquals("INVALID_DRAFTS", invalid.get("errorCode"));
        assertEquals(set("index", "field", "message"), new TreeSet<>(((List<Map<String, Object>>) invalid.get("errors")).get(0).keySet()));

        Map<String, Object> plain = handler.handleAiApi(TestCaseAiErrors.alreadyCommitted()).getBody();
        assertEquals(set("timestamp", "status", "message", "errorCode"), new TreeSet<>(plain.keySet()));
        assertEquals(409, plain.get("status"));
        assertEquals("GENERATION_ALREADY_COMMITTED", plain.get("errorCode"));
    }
}

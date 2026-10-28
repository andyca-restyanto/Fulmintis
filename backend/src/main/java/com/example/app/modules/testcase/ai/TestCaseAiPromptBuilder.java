// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/TestCaseAiPromptBuilder.java
package com.example.app.modules.testcase.ai;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Menyusun prompt generate test case dari template terversi (resources/testcase/prompts/v1/system.txt). Mengubah template =
 * menaikkan versi (folder baru + konstanta ini), supaya hasil yang mencatat promptVersion tetap bisa ditelusuri.
 * Teks requirement masuk sebagai DATA di blok JSON ber-escape: isinya tidak bisa menutup blok itu atau menyamar sbg perintah.
 */
@Component
public class TestCaseAiPromptBuilder {

    public static final String PROMPT_VERSION = "testcase-ai-v1";
    private static final String TEMPLATE = "testcase/prompts/v1/system.txt";

    public static final String TASK_OPEN = "<testcase-task>";
    public static final String TASK_CLOSE = "</testcase-task>";

    private final ObjectMapper mapper;
    private final AtomicReference<String> systemPrompt = new AtomicReference<>();

    public TestCaseAiPromptBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String systemPrompt() {
        String cached = systemPrompt.get();
        if (cached == null) {
            try (var in = new ClassPathResource(TEMPLATE).getInputStream()) {
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new IllegalStateException("Template prompt tidak ditemukan: " + TEMPLATE, e);
            }
            systemPrompt.set(cached);
        }
        return cached;
    }

    public String userPrompt(String requirement, int count, boolean includeNegative) {
        String json;
        try {
            json = mapper.writeValueAsString(new TestCaseAiTask(requirement, count, includeNegative));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal menyusun prompt", e);
        }
        // "</" -> "<\/" (escape JSON yang sah): isi requirement TIDAK BISA memuat penutup blok palsu.
        json = json.replace("</", "<\\/");
        return "Create the test case drafts for the task below. Reply with the JSON object described in the rules only.\n"
                + TASK_OPEN + "\n" + json + "\n" + TASK_CLOSE;
    }

    /** Kebalikan {@link #userPrompt}: dipakai responder palsu utk membaca tugas dari prompt. */
    public TestCaseAiTask extractTask(String userPrompt) {
        int start = userPrompt.indexOf(TASK_OPEN);
        int end = userPrompt.lastIndexOf(TASK_CLOSE);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Blok tugas tidak ditemukan di prompt");
        }
        try {
            return mapper.readValue(userPrompt.substring(start + TASK_OPEN.length(), end), TestCaseAiTask.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Isi blok tugas bukan JSON valid", e);
        }
    }

    /**
     * Skema keluaran (JSON Schema standar). Adapter yang memutuskan memakainya atau tidak (Gemini: app.ai.gemini.response-schema,
     * bawaan mati karena parameternya belum terverifikasi). Parser tetap memvalidasi semuanya, dgn atau tanpa skema.
     */
    public JsonNode responseSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode item = root.putObject("properties").putObject("testCases");
        item.put("type", "array");
        ObjectNode draft = item.putObject("items");
        draft.put("type", "object");
        ObjectNode props = draft.putObject("properties");
        props.putObject("title").put("type", "string");
        enumProperty(props, "priority", Arrays.stream(TestCasePriority.values()).map(Enum::name).toArray(String[]::new));
        enumProperty(props, "type", Arrays.stream(TestCaseType.values()).map(Enum::name).toArray(String[]::new));
        enumProperty(props, "scenarioType", Arrays.stream(TestCaseScenarioType.values()).map(Enum::name).toArray(String[]::new));
        props.putObject("description").put("type", "string");
        props.putObject("objective").put("type", "string");
        props.putObject("precondition").put("type", "string");
        ObjectNode step = props.putObject("steps").put("type", "array").putObject("items");
        step.put("type", "object");
        ObjectNode stepProps = step.putObject("properties");
        stepProps.putObject("action").put("type", "string");
        stepProps.putObject("expected").put("type", "string");
        ArrayNode required = draft.putArray("required");
        required.add("title").add("priority").add("type").add("steps");
        root.putArray("required").add("testCases");
        return root;
    }

    private static void enumProperty(ObjectNode props, String name, String[] values) {
        ObjectNode property = props.putObject(name);
        property.put("type", "string");
        ArrayNode enums = property.putArray("enum");
        for (String value : values) {
            enums.add(value);
        }
    }
}

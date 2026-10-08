// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/AutomationPromptBuilder.java
package com.example.app.modules.automation.ai;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.modules.testcase.service.importer.TestStepSplitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menyusun prompt dari template terversi di resources/automation/prompts/{PROMPT_VERSION}/.
 * Mengubah template = menaikkan versi (folder baru + konstanta ini), supaya setiap hasil generate
 * (yang mencatat promptVersion) tetap bisa ditelusuri ke template yang membuatnya.
 */
@Component
public class AutomationPromptBuilder {

    public static final String PROMPT_VERSION = "automation-v1";
    private static final String TEMPLATE_DIR = "automation/prompts/v1/";

    public static final String TASK_OPEN = "<task>";
    public static final String TASK_CLOSE = "</task>";

    // Batas ukuran masukan (menjaga jumlah token & biaya; teks test case datang dari anggota project).
    static final int MAX_FIELD_CHARS = 4000;
    static final int MAX_STEP_CHARS = 1000;
    static final int MAX_STEPS = 50;

    private final ObjectMapper mapper;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public AutomationPromptBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String systemPrompt(AutomationFramework framework, AutomationLanguage language, AutomationPattern pattern) {
        return template("system.txt").strip()
                + "\n\nFramework guidance:\n" + template("framework-" + framework.name().toLowerCase(Locale.ROOT) + ".txt").strip()
                + "\n\nLanguage guidance:\n" + template("language-" + language.name().toLowerCase(Locale.ROOT) + ".txt").strip()
                + "\n\nStructure guidance:\n" + template("pattern-" + pattern.name().toLowerCase(Locale.ROOT) + ".txt").strip();
    }

    public AutomationTask buildTask(
            AutomationFramework framework, AutomationLanguage language, AutomationPattern pattern,
            String structureNotes, List<TestCase> testCases
    ) {
        List<AutomationTask.TestCaseSpec> specs = new ArrayList<>();
        for (TestCase testCase : testCases) {
            specs.add(new AutomationTask.TestCaseSpec(
                    clip(testCase.getTitle(), MAX_FIELD_CHARS),
                    testCase.getPriority() == null ? null : testCase.getPriority().name(),
                    testCase.getType() == null ? null : testCase.getType().name(),
                    testCase.getScenarioType() == null ? null : testCase.getScenarioType().name(),
                    clip(testCase.getDescription(), MAX_FIELD_CHARS),
                    clip(testCase.getObjective(), MAX_FIELD_CHARS),
                    clip(testCase.getPrecondition(), MAX_FIELD_CHARS),
                    pairSteps(testCase.getTestStep(), testCase.getExpectedResult())));
        }
        return new AutomationTask(framework.name(), language.name(), pattern.name(),
                clip(structureNotes, MAX_FIELD_CHARS), specs);
    }

    public String userPrompt(AutomationTask task) {
        String json;
        try {
            json = mapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal menyusun prompt", e);
        }
        // "</" -> "<\/" (escape JSON yang sah): isi data TIDAK BISA memuat penutup </task> palsu
        // dan memutus pembatas blok data.
        json = json.replace("</", "<\\/");
        return "Generate the automated test code for the task below. Reply with the JSON object described in the rules only.\n"
                + TASK_OPEN + "\n" + json + "\n" + TASK_CLOSE;
    }

    /** Kebalikan {@link #userPrompt}: dipakai klien palsu utk membaca tugas dari prompt. */
    public AutomationTask extractTask(String userPrompt) {
        int start = userPrompt.indexOf(TASK_OPEN);
        int end = userPrompt.lastIndexOf(TASK_CLOSE);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Blok <task> tidak ditemukan di prompt");
        }
        try {
            return mapper.readValue(userPrompt.substring(start + TASK_OPEN.length(), end), AutomationTask.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Isi blok <task> bukan JSON valid", e);
        }
    }

    private List<AutomationTask.StepSpec> pairSteps(String testStep, String expectedResult) {
        List<String> actions = TestStepSplitter.split(testStep);
        List<String> expected = TestStepSplitter.split(expectedResult);
        int count = Math.min(Math.max(actions.size(), expected.size()), MAX_STEPS);
        List<AutomationTask.StepSpec> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            steps.add(new AutomationTask.StepSpec(
                    clip(i < actions.size() ? actions.get(i) : "", MAX_STEP_CHARS),
                    clip(i < expected.size() ? expected.get(i) : "", MAX_STEP_CHARS)));
        }
        return steps;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    private String template(String fileName) {
        return templateCache.computeIfAbsent(fileName, name -> {
            try (var in = new ClassPathResource(TEMPLATE_DIR + name).getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Template prompt tidak ditemukan: " + TEMPLATE_DIR + name, e);
            }
        });
    }
}

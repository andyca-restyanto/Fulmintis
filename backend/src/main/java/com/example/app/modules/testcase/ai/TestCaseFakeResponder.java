// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/TestCaseFakeResponder.java
package com.example.app.modules.testcase.ai;

import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.FakeAiResponder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Responder PALSU utk generate test case (dipakai FakeAiClient saat app.ai.provider=fake, hanya dev/test): membuat draft contoh dari
 * kata-kata awal requirement, tanpa memanggil layanan apa pun. Setiap deskripsi bertanda "FAKE AI OUTPUT" supaya tidak pernah
 * disangka hasil AI sungguhan.
 */
@Component
public class TestCaseFakeResponder implements FakeAiResponder {

    private static final String BANNER = "FAKE AI OUTPUT (dev/test) - bukan hasil AI sungguhan";

    // {judul, prioritas, skenario}; %s = topik. Urutan: alur utama dulu, lalu batas, lalu negatif.
    private static final String[][] TEMPLATES = {
            {"%s berhasil dengan data valid", "HIGH", "POSITIVE"},
            {"%s pada nilai batas minimum", "MEDIUM", "POSITIVE"},
            {"%s ditolak bila data wajib kosong", "HIGH", "NEGATIVE"},
            {"%s pada nilai batas maksimum", "MEDIUM", "POSITIVE"},
            {"%s ditolak untuk pengguna tanpa hak akses", "HIGH", "NEGATIVE"},
            {"%s dengan format data tidak valid", "MEDIUM", "NEGATIVE"},
            {"%s dapat diulang tanpa efek samping", "LOW", "POSITIVE"},
    };

    private final ObjectMapper mapper;
    private final TestCaseAiPromptBuilder promptBuilder;

    public TestCaseFakeResponder(ObjectMapper mapper, TestCaseAiPromptBuilder promptBuilder) {
        this.mapper = mapper;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public boolean supports(AiRequest request) {
        return request.userPrompt() != null && request.userPrompt().contains(TestCaseAiPromptBuilder.TASK_OPEN);
    }

    @Override
    public String respond(AiRequest request, boolean truncate) {
        TestCaseAiTask task = promptBuilder.extractTask(request.userPrompt());
        String topic = topic(task.requirement());

        List<Map<String, Object>> drafts = new ArrayList<>();
        int made = 0;
        for (int round = 0; made < task.count(); round++) {
            for (String[] template : TEMPLATES) {
                if (made >= task.count()) {
                    break;
                }
                boolean negative = template[2].equals("NEGATIVE");
                if (negative && !task.includeNegative()) {
                    continue;
                }
                String suffix = round == 0 ? "" : " (variasi " + (round + 1) + ")";
                drafts.add(draft(String.format(template[0], topic) + suffix, template[1], template[2]));
                made++;
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("testCases", drafts);
        String json;
        try {
            json = mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        // Terpotong di tengah draft terakhir yang diminta: draft sebelumnya tetap lengkap.
        return truncate ? json.substring(0, Math.max(1, (int) (json.length() * 0.6))) : json;
    }

    private static Map<String, Object> draft(String title, String priority, String scenario) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("title", title);
        draft.put("priority", priority);
        draft.put("type", "MANUAL");
        draft.put("scenarioType", scenario);
        draft.put("description", BANNER + ". Draft contoh untuk diuji alur review.");
        draft.put("objective", "Memastikan perilaku sesuai requirement.");
        draft.put("precondition", "Pengguna sudah login.");
        draft.put("steps", List.of(
                Map.of("action", "Buka halaman fitur", "expected", "Halaman fitur tampil"),
                Map.of("action", "Isi data sesuai skenario", "expected", "Data diterima form"),
                Map.of("action", "Kirim form", "expected", scenario.equals("NEGATIVE") ? "Sistem menampilkan pesan kesalahan" : "Sistem memproses dengan sukses")));
        return draft;
    }

    /** Beberapa kata pertama requirement sebagai topik judul. */
    static String topic(String requirement) {
        String[] words = (requirement == null ? "" : requirement.strip()).split("\\s+");
        StringBuilder topic = new StringBuilder();
        for (int i = 0; i < Math.min(5, words.length); i++) {
            if (!words[i].isEmpty()) {
                topic.append(topic.length() == 0 ? "" : " ").append(words[i].replaceAll("[\"\\\\]", ""));
            }
        }
        return topic.length() == 0 ? "Fitur" : topic.toString();
    }
}

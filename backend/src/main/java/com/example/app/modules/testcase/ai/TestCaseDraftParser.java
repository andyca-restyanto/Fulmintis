// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/TestCaseDraftParser.java
package com.example.app.modules.testcase.ai;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.TestCaseDraftDTO;
import com.example.app.modules.testcase.dto.TestCaseDraftStepDTO;
import com.example.app.modules.testcase.service.importer.TestCaseEnumValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membaca & membersihkan keluaran AI menjadi draft test case.
 * <ul>
 *   <li>Tahan terhadap JSON yang dibungkus blok kode/kalimat pengantar.</li>
 *   <li><b>Keluaran terpotong (batas token)</b>: draft yang sudah LENGKAP diselamatkan, yang terpotong dibuang, hasil ditandai
 *       {@code truncated}. Hanya kalau tidak ada satu pun yang lengkap, parsing gagal.</li>
 *   <li>AI diperlakukan sbg sumber tidak tepercaya: nilai enum yang salah dinormalkan ke bawaan, teks dibersihkan dari karakter
 *       kontrol dan dibatasi panjangnya, judul kembar dalam satu batch dibuang, dan jumlah dipangkas sesuai yang diminta.</li>
 * </ul>
 */
@Component
public class TestCaseDraftParser {

    public static final int MAX_TITLE_CHARS = 255;
    public static final int MAX_TEXT_CHARS = 4000;
    public static final int MAX_STEPS = 30;
    public static final int MAX_STEP_CHARS = 1000;

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");
    private static final Pattern ARRAY_KEY = Pattern.compile("(?i)\"test[_ ]?cases\"\\s*:\\s*\\[");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u2028\\u2029]");

    private final ObjectMapper mapper;

    public TestCaseDraftParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ParsedDrafts parse(String raw, int requestedCount) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDraftOutputException("keluaran kosong");
        }

        String text = raw.strip();
        Matcher fenced = CODE_FENCE.matcher(text);
        if (fenced.find() && (fenced.group(1).indexOf('[') >= 0 || fenced.group(1).indexOf('{') >= 0)) {
            text = fenced.group(1);
        }

        int arrayStart = findArrayStart(text);
        if (arrayStart < 0) {
            throw new InvalidDraftOutputException("tidak ada daftar testCases di keluaran");
        }

        // ---- Ambil objek-objek LENGKAP di dalam array (string-aware); yang terpotong dibuang ----
        List<String> objects = new ArrayList<>();
        boolean closed = false;
        int i = arrayStart + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ']') {
                closed = true;
                break;
            }
            if (c == '{') {
                int end = findObjectEnd(text, i);
                if (end < 0) {
                    break; // objek terakhir terpotong
                }
                objects.add(text.substring(i, end + 1));
                i = end + 1;
                continue;
            }
            i++;
        }
        boolean truncated = !closed;

        // ---- Bersihkan tiap draft ----
        List<TestCaseDraftDTO> drafts = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        int discarded = 0;
        for (String objectText : objects) {
            TestCaseDraftDTO draft = toDraft(objectText);
            if (draft == null || !seenTitles.add(normalizeTitle(draft.title()))) {
                discarded++;
                continue;
            }
            if (drafts.size() >= Math.max(1, requestedCount)) {
                discarded++; // AI memberi lebih dari yang diminta: dipangkas
                continue;
            }
            drafts.add(draft);
        }

        if (drafts.isEmpty()) {
            throw new InvalidDraftOutputException("tidak ada draft yang lengkap dan valid (objek ditemukan: " + objects.size()
                    + ", terpotong: " + truncated + ")");
        }

        List<TestCaseDraftDTO> numbered = new ArrayList<>(drafts.size());
        for (int n = 0; n < drafts.size(); n++) {
            TestCaseDraftDTO d = drafts.get(n);
            numbered.add(new TestCaseDraftDTO("d" + (n + 1), d.title(), d.priority(), d.type(), d.scenarioType(),
                    d.description(), d.objective(), d.precondition(), d.steps(), false));
        }
        return new ParsedDrafts(numbered, truncated, discarded);
    }

    // ---------- Penemuan struktur ----------

    private static int findArrayStart(String text) {
        Matcher key = ARRAY_KEY.matcher(text);
        if (key.find()) {
            return key.end() - 1; // posisi '['
        }
        // AI kadang mengembalikan array telanjang.
        int first = 0;
        while (first < text.length() && Character.isWhitespace(text.charAt(first))) {
            first++;
        }
        return first < text.length() && text.charAt(first) == '[' ? first : -1;
    }

    /** Indeks '}' penutup objek yang dimulai di {@code start}, atau -1 kalau terpotong. Peka terhadap string & escape. */
    private static int findObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // ---------- Pembersihan satu draft ----------

    /** @return draft, atau null kalau tidak layak (tanpa judul / tanpa langkah / bukan objek). */
    private TestCaseDraftDTO toDraft(String objectText) {
        JsonNode node;
        try {
            node = mapper.readTree(objectText);
        } catch (IOException e) {
            return null;
        }
        if (!node.isObject()) {
            return null;
        }

        String title = singleLine(node.path("title").asText(""), MAX_TITLE_CHARS);
        if (title.isEmpty()) {
            return null;
        }

        List<TestCaseDraftStepDTO> steps = new ArrayList<>();
        for (JsonNode step : node.path("steps")) {
            if (steps.size() >= MAX_STEPS) {
                break;
            }
            String action;
            String expected;
            if (step.isObject()) {
                action = singleLine(step.path("action").asText(""), MAX_STEP_CHARS);
                expected = singleLine(step.path("expected").asText(""), MAX_STEP_CHARS);
            } else if (step.isTextual()) {
                action = singleLine(step.asText(), MAX_STEP_CHARS);
                expected = "";
            } else {
                continue;
            }
            if (!action.isEmpty() || !expected.isEmpty()) {
                steps.add(new TestCaseDraftStepDTO(action, expected));
            }
        }
        if (steps.isEmpty()) {
            return null; // test case tanpa langkah tidak bisa dieksekusi
        }

        // Nilai enum tidak dikenal dinormalkan (bukan dibuang): user masih meninjau & bisa mengubahnya di layar review.
        TestCasePriority priority = TestCaseEnumValues.priority(node.path("priority").asText("")).orElse(TestCasePriority.MEDIUM);
        TestCaseType type = TestCaseEnumValues.type(node.path("type").asText("")).orElse(TestCaseType.MANUAL);
        TestCaseScenarioType scenario = TestCaseEnumValues.scenario(node.path("scenarioType").asText("")).orElse(null);

        return new TestCaseDraftDTO(null, title, priority, type, scenario,
                multiLine(node.path("description").asText(""), MAX_TEXT_CHARS),
                multiLine(node.path("objective").asText(""), MAX_TEXT_CHARS),
                multiLine(node.path("precondition").asText(""), MAX_TEXT_CHARS),
                steps, false);
    }

    static String singleLine(String value, int max) {
        String clean = CONTROL_CHARS.matcher(value == null ? "" : value).replaceAll("").replaceAll("\\s+", " ").strip();
        return clean.length() > max ? clean.substring(0, max).strip() : clean;
    }

    /** Boleh beberapa baris; hanya karakter kontrol yang dibuang. Kosong -> null. */
    static String multiLine(String value, int max) {
        String clean = CONTROL_CHARS.matcher(value == null ? "" : value).replaceAll("").strip();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > max ? clean.substring(0, max).strip() : clean;
    }

    private static String normalizeTitle(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}

// filepath: /backend/src/test/java/com/example/app/modules/testcase/ai/TestCaseDraftParserTest.java
package com.example.app.modules.testcase.ai;

import com.example.app.modules.testcase.TestCasePriority;
import com.example.app.modules.testcase.TestCaseScenarioType;
import com.example.app.modules.testcase.TestCaseType;
import com.example.app.modules.testcase.dto.TestCaseDraftDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseDraftParserTest {

    private final TestCaseDraftParser parser = new TestCaseDraftParser(new com.fasterxml.jackson.databind.ObjectMapper());

    private static String draft(String title) {
        return "{\"title\":\"" + title + "\",\"priority\":\"HIGH\",\"type\":\"MANUAL\",\"scenarioType\":\"POSITIVE\","
                + "\"description\":\"d\",\"objective\":\"o\",\"precondition\":\"p\","
                + "\"steps\":[{\"action\":\"a1\",\"expected\":\"e1\"},{\"action\":\"a2\",\"expected\":\"e2\"}]}";
    }

    private static String wrap(String... drafts) {
        return "{\"testCases\":[" + String.join(",", drafts) + "]}";
    }

    // ---------- Bentuk keluaran ----------

    @Test
    void parsesPlainJsonIntoNumberedDrafts() {
        ParsedDrafts parsed = parser.parse(wrap(draft("Login berhasil"), draft("Login gagal")), 5);

        assertEquals(2, parsed.drafts().size());
        assertFalse(parsed.truncated());
        TestCaseDraftDTO first = parsed.drafts().get(0);
        assertEquals("d1", first.tempId());
        assertEquals("d2", parsed.drafts().get(1).tempId());
        assertEquals("Login berhasil", first.title());
        assertEquals(TestCasePriority.HIGH, first.priority());
        assertEquals(TestCaseType.MANUAL, first.type());
        assertEquals(TestCaseScenarioType.POSITIVE, first.scenarioType());
        assertEquals(2, first.steps().size());
        assertEquals("a2", first.steps().get(1).action());
        assertEquals("e2", first.steps().get(1).expected());
        assertFalse(first.duplicateOfExisting());
    }

    @Test
    void toleratesMarkdownFencesProseAndABareArray() {
        String fenced = "Tentu, ini hasilnya:\n```json\n" + wrap(draft("A")) + "\n```\nSemoga membantu {dengan kurung}.";
        assertEquals(1, parser.parse(fenced, 5).drafts().size());

        String bareArray = "[" + draft("A") + "," + draft("B") + "]";
        assertEquals(2, parser.parse(bareArray, 5).drafts().size());

        String altKey = "{\"test_cases\":[" + draft("A") + "]}";
        assertEquals(1, parser.parse(altKey, 5).drafts().size());
    }

    @Test
    void bracesAndEscapedQuotesInsideTextDoNotConfuseTheScanner() {
        String tricky = "{\"title\":\"Format {a: \\\"}\\\"} valid\",\"priority\":\"LOW\",\"type\":\"MANUAL\","
                + "\"steps\":[{\"action\":\"isi {x}\",\"expected\":\"ok ]\"}]}";

        ParsedDrafts parsed = parser.parse(wrap(tricky, draft("B")), 5);

        assertEquals(2, parsed.drafts().size());
        assertEquals("Format {a: \"}\"} valid", parsed.drafts().get(0).title());
    }

    // ---------- Keluaran terpotong ----------

    @Test
    void aTruncatedAnswerKeepsTheCompleteDraftsAndFlagsIt() {
        String complete = wrap(draft("Lengkap 1"), draft("Lengkap 2"), draft("Terpotong"));
        String cut = complete.substring(0, complete.length() - 40); // putus di tengah draft ke-3

        ParsedDrafts parsed = parser.parse(cut, 5);

        assertTrue(parsed.truncated());
        assertEquals(2, parsed.drafts().size());
        assertEquals("Lengkap 2", parsed.drafts().get(1).title());
    }

    @Test
    void ifNothingCompleteSurvivesTheParseFails() {
        String onlyPartial = "{\"testCases\":[{\"title\":\"Terpotong tanpa penutup\",\"priority\":\"HI";
        assertThrows(InvalidDraftOutputException.class, () -> parser.parse(onlyPartial, 3));
    }

    @Test
    void unusableOutputIsRejected() {
        for (String bad : List.of("", "   ", "Maaf saya tidak bisa.", "{\"foo\":1}", "{\"testCases\":[]}", "{\"testCases\":\"x\"}")) {
            assertThrows(InvalidDraftOutputException.class, () -> parser.parse(bad, 3), bad);
        }
        assertThrows(InvalidDraftOutputException.class, () -> parser.parse(null, 3));
    }

    // ---------- Pembersihan & pembatasan ----------

    @Test
    void extraDraftsBeyondTheRequestedCountAreTrimmed() {
        ParsedDrafts parsed = parser.parse(wrap(draft("A"), draft("B"), draft("C"), draft("D"), draft("E")), 3);

        assertEquals(3, parsed.drafts().size());
        assertEquals("C", parsed.drafts().get(2).title());
        assertEquals(2, parsed.discarded());
    }

    @Test
    void duplicateTitlesInsideOneBatchAreDroppedIgnoringCaseAndSpacing() {
        ParsedDrafts parsed = parser.parse(wrap(draft("Login  Berhasil"), draft("login berhasil"), draft("Lain")), 5);

        assertEquals(2, parsed.drafts().size());
        assertEquals(1, parsed.discarded());
    }

    @Test
    void draftsWithoutATitleOrWithoutStepsAreDiscarded() {
        String noTitle = "{\"title\":\"  \",\"priority\":\"HIGH\",\"type\":\"MANUAL\",\"steps\":[{\"action\":\"a\",\"expected\":\"b\"}]}";
        String noSteps = "{\"title\":\"Tanpa langkah\",\"priority\":\"HIGH\",\"type\":\"MANUAL\",\"steps\":[]}";
        String blankSteps = "{\"title\":\"Langkah kosong\",\"priority\":\"HIGH\",\"type\":\"MANUAL\",\"steps\":[{\"action\":\"\",\"expected\":\" \"}]}";

        ParsedDrafts parsed = parser.parse(wrap(noTitle, noSteps, blankSteps, draft("Baik")), 5);

        assertEquals(1, parsed.drafts().size());
        assertEquals("Baik", parsed.drafts().get(0).title());
        assertEquals(3, parsed.discarded());
    }

    @Test
    void unknownEnumValuesAreNormalisedNotDiscardedAndSynonymsAreAccepted() {
        String odd = "{\"title\":\"Aneh\",\"priority\":\"URGENT\",\"type\":\"HYBRID\",\"scenarioType\":\"NEUTRAL\","
                + "\"steps\":[{\"action\":\"a\",\"expected\":\"b\"}]}";
        String synonyms = "{\"title\":\"Sinonim\",\"priority\":\" highest \",\"type\":\"automated\",\"scenarioType\":\"negatif\","
                + "\"steps\":[{\"action\":\"a\",\"expected\":\"b\"}]}";

        ParsedDrafts parsed = parser.parse(wrap(odd, synonyms), 5);

        TestCaseDraftDTO normalised = parsed.drafts().get(0);
        assertEquals(TestCasePriority.MEDIUM, normalised.priority());
        assertEquals(TestCaseType.MANUAL, normalised.type());
        assertNull(normalised.scenarioType());
        TestCaseDraftDTO mapped = parsed.drafts().get(1);
        assertEquals(TestCasePriority.HIGHEST, mapped.priority());
        assertEquals(TestCaseType.AUTOMATION, mapped.type());
        assertEquals(TestCaseScenarioType.NEGATIVE, mapped.scenarioType());
    }

    @Test
    void textIsCleanedAndCapped() {
        String longTitle = "T".repeat(400);
        String raw = "{\"title\":\"" + longTitle + "\",\"priority\":\"LOW\",\"type\":\"MANUAL\","
                + "\"description\":\"baris1\\nbaris2\\u0000\\u0007x\",\"objective\":\"   \",\"precondition\":\"" + "p".repeat(9000) + "\","
                + "\"steps\":[{\"action\":\"langkah\\nbaris baru\\t panjang  spasi\",\"expected\":\"" + "e".repeat(2000) + "\"}]}";

        TestCaseDraftDTO draft = parser.parse(wrap(raw), 3).drafts().get(0);

        assertEquals(255, draft.title().length());
        assertEquals("baris1\nbaris2x", draft.description());           // beberapa baris boleh; karakter kontrol dibuang
        assertNull(draft.objective());                                    // kosong -> null
        assertEquals(4000, draft.precondition().length());
        assertEquals("langkah baris baru panjang spasi", draft.steps().get(0).action()); // langkah satu baris
        assertEquals(1000, draft.steps().get(0).expected().length());
    }

    @Test
    void stepCountIsCappedAndStepsGivenAsPlainStringsBecomeActions() {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            steps.append(i == 0 ? "" : ",").append("{\"action\":\"a").append(i).append("\",\"expected\":\"e\"}");
        }
        String many = "{\"title\":\"Banyak\",\"priority\":\"LOW\",\"type\":\"MANUAL\",\"steps\":[" + steps + "]}";
        String strings = "{\"title\":\"String\",\"priority\":\"LOW\",\"type\":\"MANUAL\",\"steps\":[\"buka\",\"klik\"]}";

        ParsedDrafts parsed = parser.parse(wrap(many, strings), 5);

        assertEquals(30, parsed.drafts().get(0).steps().size());
        assertEquals("buka", parsed.drafts().get(1).steps().get(0).action());
        assertEquals("", parsed.drafts().get(1).steps().get(0).expected());
    }
}

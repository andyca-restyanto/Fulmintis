// filepath: /backend/src/test/java/com/example/app/modules/automation/FakeAiClientTest.java
package com.example.app.modules.automation;

import com.example.app.modules.automation.ai.AutomationOutputParser;
import com.example.app.modules.automation.ai.AutomationPromptBuilder;
import com.example.app.modules.automation.ai.AutomationTask;
import com.example.app.modules.automation.ai.FakeAiClient;
import com.example.app.modules.automation.ai.GeneratedPaths;
import com.example.app.modules.automation.ai.InvalidAiOutputException;
import com.example.app.modules.automation.ai.ParsedOutput;
import com.example.app.modules.automation.dto.AutomationFileDTO;
import com.example.app.modules.project.entity.Project;
import com.example.app.modules.testcase.entity.TestCase;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.AiResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAiClientTest {

    private final AutomationPromptBuilder promptBuilder = new AutomationPromptBuilder(AutomationTestSupport.mapper());
    private final AutomationOutputParser parser = new AutomationOutputParser(AutomationTestSupport.mapper());
    private final Project project = Project.builder().id(UUID.randomUUID()).build();

    private FakeAiClient client(String mode) {
        return new FakeAiClient(AutomationTestSupport.mapper(), promptBuilder, mode, 0);
    }

    private List<TestCase> cases() {
        return List.of(
                AutomationTestSupport.testCase(project, "Login berhasil", "1. Buka login\n2. Klik Sign In", "1. Form tampil\n2. Masuk dashboard"),
                AutomationTestSupport.testCase(project, "Login berhasil", "1. Ulangi", "1. Sama"),      // judul sama -> nama berkas bentrok
                AutomationTestSupport.testCase(project, "!!!", null, null),                              // judul tanpa huruf/angka
                AutomationTestSupport.testCase(project, "123 mulai angka", "1. a", "1. b"));
    }

    private AiRequest request(AutomationFramework f, AutomationLanguage l, AutomationPattern p, List<TestCase> cases) {
        AutomationTask task = promptBuilder.buildTask(f, l, p, null, cases);
        return new AiRequest(promptBuilder.systemPrompt(f, l, p), promptBuilder.userPrompt(task), "fake-model", 4096, Duration.ofSeconds(30));
    }

    private ParsedOutput generate(AutomationFramework f, AutomationLanguage l, AutomationPattern p, List<TestCase> cases) {
        AiResponse response = client("NONE").generate(request(f, l, p, cases));
        return parser.parse(response.text()); // lewat parser ASLI: keluaran palsu harus lolos validasi yang sama dgn keluaran sungguhan
    }

    private static String expectedExtension(AutomationFramework f, AutomationLanguage l) {
        return f == AutomationFramework.ROBOT_FRAMEWORK ? "robot"
                : f == AutomationFramework.CYPRESS ? "cy." + l.sourceExtension()
                : f == AutomationFramework.SELENIUM && l != AutomationLanguage.JAVA && l != AutomationLanguage.PYTHON ? "test." + l.sourceExtension()
                : f == AutomationFramework.PLAYWRIGHT && l != AutomationLanguage.JAVA && l != AutomationLanguage.PYTHON ? "spec." + l.sourceExtension()
                : l.sourceExtension();
    }

    @Test
    void everyValidCombinationProducesParseableSafeOutputWithOneTestPerTestCase() {
        int combinations = 0;
        for (AutomationFramework framework : AutomationFramework.values()) {
            for (AutomationLanguage language : AutomationLanguage.values()) {
                if (!AutomationCompatibility.isSupported(framework, language)) {
                    continue;
                }
                for (AutomationPattern pattern : AutomationPattern.values()) {
                    combinations++;
                    String label = framework + "/" + language + "/" + pattern;
                    List<AutomationFileDTO> files = generate(framework, language, pattern, cases()).files();

                    boolean robot = framework == AutomationFramework.ROBOT_FRAMEWORK;
                    // + requirements.txt dan README.md utk Robot
                    int expectedFiles = cases().size() + (pattern == AutomationPattern.PAGE_OBJECT_MODEL ? 1 : 0) + (robot ? 2 : 0);
                    assertEquals(expectedFiles, files.size(), label);

                    Set<String> paths = new HashSet<>();
                    for (AutomationFileDTO file : files) {
                        assertTrue(GeneratedPaths.isSafe(file.path()), label + " path tidak aman: " + file.path());
                        assertTrue(paths.add(file.path().toLowerCase()), label + " path ganda: " + file.path());
                        assertTrue(file.content().contains("FAKE AI OUTPUT"), label + " tanpa tanda FAKE");
                    }

                    List<AutomationFileDTO> testFiles = files.stream()
                            .filter(f -> !f.path().contains("BasePage") && !f.path().contains("base_page")
                                    && !f.path().equals("requirements.txt") && !f.path().equals("README.md")).toList();
                    assertEquals(cases().size(), testFiles.size(), label);
                    for (AutomationFileDTO testFile : testFiles) {
                        assertTrue(testFile.path().endsWith("." + expectedExtension(framework, language)),
                                label + " ekstensi salah: " + testFile.path());
                    }
                    // langkah test case tercermin (komentar), mis. "Step 1: Buka login"
                    assertTrue(testFiles.get(0).content().contains("Step 1: Buka login -> expected: Form tampil"), label);
                }
            }
        }
        assertEquals(11 * 2, combinations); // 11 kombinasi valid (10 + Robot/Python) x 2 pola
    }

    @Test
    void robotFrameworkOutputUsesRobotSyntaxWithResourceFilesForPageObjectModel() {
        List<AutomationFileDTO> pom = generate(AutomationFramework.ROBOT_FRAMEWORK, AutomationLanguage.PYTHON,
                AutomationPattern.PAGE_OBJECT_MODEL, cases()).files();
        List<String> pomPaths = pom.stream().map(AutomationFileDTO::path).toList();

        assertTrue(pomPaths.contains("resources/pages/base_page.resource"));
        assertTrue(pomPaths.contains("requirements.txt"));
        assertTrue(pomPaths.contains("README.md"));
        assertTrue(pomPaths.contains("tests/login-berhasil.robot"));
        assertTrue(pomPaths.contains("tests/login-berhasil-2.robot"));      // judul kembar dibedakan
        String test = pom.stream().filter(f -> f.path().equals("tests/login-berhasil.robot")).findFirst().orElseThrow().content();
        assertTrue(test.contains("*** Settings ***") && test.contains("*** Test Cases ***"));
        assertTrue(test.contains("Resource          ../resources/pages/base_page.resource"));
        assertTrue(test.contains("Open Application"));
        assertFalse(test.contains("Library           SeleniumLibrary"), "POM: library dimuat di resource, bukan di test");
        assertTrue(test.contains("Step 1: Buka login -> expected: Form tampil"));
        String requirements = pom.stream().filter(f -> f.path().equals("requirements.txt")).findFirst().orElseThrow().content();
        assertTrue(requirements.contains("robotframework-seleniumlibrary"));

        List<AutomationFileDTO> simple = generate(AutomationFramework.ROBOT_FRAMEWORK, AutomationLanguage.PYTHON,
                AutomationPattern.SIMPLE, cases()).files();
        assertTrue(simple.stream().noneMatch(f -> f.path().endsWith(".resource")), "Simple: tanpa resource file");
        String simpleTest = simple.stream().filter(f -> f.path().equals("tests/login-berhasil.robot")).findFirst().orElseThrow().content();
        assertTrue(simpleTest.contains("Library           SeleniumLibrary"));
        assertTrue(simpleTest.contains("Open Browser    ${BASE_URL}    chrome"));
    }

    @Test
    void robotTitlesCannotInjectVariablesCommentsOrNewKeywordLines() {
        TestCase tricky = AutomationTestSupport.testCase(project, "#Judul ${SECRET} @{LIST} %{HOME} &{D} \\ a\n    Run Process    rm -rf /",
                "1. buka\n    Run Process    evil", "1. ok");

        String content = generate(AutomationFramework.ROBOT_FRAMEWORK, AutomationLanguage.PYTHON, AutomationPattern.SIMPLE, List.of(tricky))
                .files().get(0).content();

        assertTrue(content.contains("\\#Judul \\${SECRET} \\@{LIST} \\%{HOME} \\&{D} \\\\ a Run Process rm -rf /"), content);
        assertFalse(content.contains("\n    Run Process"), "baris baru dari judul/langkah tidak boleh membuat baris keyword baru");
        assertEquals(-1, content.indexOf("\n    Run Process"));
    }

    @Test
    void invalidCombinationNeverReachesTheClient() {
        // Cypress+Java ditolak di service sebelum prompt dibuat; di level klien palsu pun tidak ada templatenya.
        assertFalse(AutomationCompatibility.isSupported(AutomationFramework.CYPRESS, AutomationLanguage.JAVA));
    }

    @Test
    void titlesWithQuotesBackslashesAndNewlinesCannotBreakOutOfStringsOrComments() {
        TestCase tricky = AutomationTestSupport.testCase(project, "It's \"quoted\" \\ back\n// injected(); evil()",
                "1. buka\n// langkah-injeksi", "1. ok");

        String js = generate(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE, List.of(tricky))
                .files().get(0).content();
        String py = generate(AutomationFramework.PLAYWRIGHT, AutomationLanguage.PYTHON, AutomationPattern.SIMPLE, List.of(tricky))
                .files().get(0).content();
        String java = generate(AutomationFramework.SELENIUM, AutomationLanguage.JAVA, AutomationPattern.SIMPLE, List.of(tricky))
                .files().get(0).content();

        assertTrue(js.contains("test('It\\'s \"quoted\" \\\\ back // injected(); evil()'"), js);
        assertTrue(py.contains("\"\"\"It's \\\"quoted\\\" \\\\ back // injected(); evil()\"\"\""), py);
        assertTrue(java.contains("@DisplayName(\"It's \\\"quoted\\\" \\\\ back // injected(); evil()\")"), java);
        for (String code : List.of(js, py, java)) {
            assertFalse(code.contains("\n// injected"), "baris baru dari judul tidak boleh membuat baris kode baru");
            assertFalse(code.contains("\n// langkah-injeksi"), "baris baru dari langkah tidak boleh membuat baris kode baru");
        }
    }

    @Test
    void javaClassNamesNeverStartWithADigitAndFallbackNamesExist() {
        List<AutomationFileDTO> files = generate(AutomationFramework.SELENIUM, AutomationLanguage.JAVA, AutomationPattern.SIMPLE, cases()).files();
        List<String> classNames = files.stream().map(f -> f.path().substring(f.path().lastIndexOf('/') + 1)).toList();

        assertTrue(classNames.contains("LoginBerhasilTest.java"));
        assertTrue(classNames.contains("LoginBerhasil2Test.java"));   // judul kembar dibedakan
        assertTrue(classNames.contains("TestCase3Test.java"));         // "!!!" -> nama cadangan
        assertTrue(classNames.contains("Tc123MulaiAngkaTest.java"));   // diawali angka -> diberi awalan
    }

    @Test
    void failureModesMapToTheMatchingProviderFailureKind() {
        AiRequest req = request(AutomationFramework.PLAYWRIGHT, AutomationLanguage.TYPESCRIPT, AutomationPattern.SIMPLE, cases());
        assertKind("QUOTA_EXHAUSTED", AiProviderException.Kind.QUOTA_EXHAUSTED, req);
        assertKind("RATE_LIMITED", AiProviderException.Kind.RATE_LIMITED, req);
        assertKind("UNAVAILABLE", AiProviderException.Kind.UNAVAILABLE, req);
        assertKind("TIMEOUT", AiProviderException.Kind.TIMEOUT, req);
        assertKind("REJECTED", AiProviderException.Kind.REJECTED, req);
    }

    private void assertKind(String mode, AiProviderException.Kind expected, AiRequest req) {
        AiProviderException e = assertThrows(AiProviderException.class, () -> client(mode).generate(req));
        assertEquals(expected, e.getKind());
    }

    @Test
    void invalidOutputModeReturnsNonJsonAndWrappedModeStillParses() {
        AiRequest req = request(AutomationFramework.CYPRESS, AutomationLanguage.JAVASCRIPT, AutomationPattern.SIMPLE, cases());

        assertThrows(InvalidAiOutputException.class, () -> parser.parse(client("INVALID_OUTPUT").generate(req).text()));

        String wrapped = client("WRAPPED_OUTPUT").generate(req).text();
        assertTrue(wrapped.startsWith("Tentu") && wrapped.contains("```json"));
        assertEquals(cases().size(), parser.parse(wrapped).files().size());
    }

    @Test
    void reportsTokenUsageAndModelAndRejectsUnknownMode() {
        AiResponse response = client("NONE").generate(request(AutomationFramework.PLAYWRIGHT, AutomationLanguage.PYTHON, AutomationPattern.SIMPLE, cases()));

        assertTrue(response.inputTokens() > 0 && response.outputTokens() > 0);
        assertEquals("fake-model", response.model());
        assertThrows(IllegalArgumentException.class, () -> client("TIDAK_ADA"));
        assertEquals("fake", client("none").providerId()); // mode tidak peka huruf besar/kecil
    }
}

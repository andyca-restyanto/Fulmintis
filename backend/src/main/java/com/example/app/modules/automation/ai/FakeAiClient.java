// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/FakeAiClient.java
package com.example.app.modules.automation.ai;

import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import com.example.app.modules.automation.dto.AutomationFileDTO;
import com.example.app.shared.ai.AiClient;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.AiResponse;
import com.example.app.shared.ai.FakeAiResponder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Klien AI PALSU utk dev/uji (app.ai.provider=fake): membaca tugas dari prompt dan membalas kode contoh
 * yang plausibel utk tiap kombinasi framework x bahasa, tanpa memanggil layanan apa pun. Dipakai agar
 * seluruh alur (setup -> generate -> hasil -> unduh -> pesan error) bisa diuji sebelum provider sungguhan ada.
 * <p>
 * Mode kegagalan (app.ai.fake.mode) mensimulasikan tiap jenis kegagalan provider. Bean ini HANYA ada kalau
 * provider=fake, dan AiConfigurationValidator menolak start kalau itu terjadi di profil prod.
 * Setiap berkas diberi tanda "FAKE AI OUTPUT" supaya tidak pernah disangka hasil AI sungguhan.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fake")
public class FakeAiClient implements AiClient {

    public enum Mode {
        NONE, QUOTA_EXHAUSTED, RATE_LIMITED, UNAVAILABLE, TIMEOUT, REJECTED,
        /** Balasan yang bukan JSON. */
        INVALID_OUTPUT,
        /** JSON benar tetapi dibungkus kalimat pengantar + blok kode (perilaku umum LLM tanpa mode JSON). */
        WRAPPED_OUTPUT,
        /** JSON terpotong di tengah (mensimulasikan batas token tercapai). Hanya berpengaruh pada fitur yang punya responder. */
        TRUNCATED_OUTPUT,
        /** JSON benar, tetapi AI menambahkan berkas pendamping yang tidak didukung (.gitignore, Dockerfile): kasus nyata dari Gemini. */
        EXTRA_FILES
    }

    private static final String BANNER = "FAKE AI OUTPUT (dev/test) - bukan hasil AI sungguhan";

    private final ObjectMapper mapper;
    private final AutomationPromptBuilder promptBuilder;
    private final Mode mode;
    private final long delayMillis;
    private final List<FakeAiResponder> responders;

    /** Dipakai Spring: fitur lain mendaftarkan FakeAiResponder-nya sendiri (mis. generate test case). */
    @Autowired
    public FakeAiClient(
            ObjectMapper mapper,
            AutomationPromptBuilder promptBuilder,
            @Value("${app.ai.fake.mode:NONE}") String mode,
            @Value("${app.ai.fake.delay-millis:1500}") long delayMillis,
            ObjectProvider<FakeAiResponder> responders
    ) {
        this(mapper, promptBuilder, mode, delayMillis, responders.orderedStream().toList());
    }

    public FakeAiClient(ObjectMapper mapper, AutomationPromptBuilder promptBuilder, String mode, long delayMillis) {
        this(mapper, promptBuilder, mode, delayMillis, List.of());
    }

    public FakeAiClient(ObjectMapper mapper, AutomationPromptBuilder promptBuilder, String mode, long delayMillis,
                        List<FakeAiResponder> responders) {
        this.mapper = mapper;
        this.promptBuilder = promptBuilder;
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        this.delayMillis = Math.max(0, delayMillis);
        this.responders = responders;
    }

    @Override
    public String providerId() {
        return "fake";
    }

    @Override
    public AiResponse generate(AiRequest request) {
        pause();

        switch (mode) {
            case QUOTA_EXHAUSTED -> throw new AiProviderException(AiProviderException.Kind.QUOTA_EXHAUSTED, "FAKE: simulasi kuota/saldo provider habis");
            case RATE_LIMITED -> throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED, "FAKE: simulasi rate limit");
            case UNAVAILABLE -> throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "FAKE: simulasi provider tidak tersedia");
            case TIMEOUT -> throw new AiProviderException(AiProviderException.Kind.TIMEOUT, "FAKE: simulasi timeout");
            case REJECTED -> throw new AiProviderException(AiProviderException.Kind.REJECTED, "FAKE: simulasi permintaan ditolak");
            case INVALID_OUTPUT -> {
                return new AiResponse("Maaf, saya tidak bisa membuat kode itu.", tokens(request.userPrompt()), 8, modelName(request));
            }
            default -> {
                // lanjut membuat keluaran normal
            }
        }

        // Fitur lain (mis. generate test case) punya responder sendiri; prompt Automation tidak dikenali mereka.
        for (FakeAiResponder responder : responders) {
            if (responder.supports(request)) {
                String body = responder.respond(request, mode == Mode.TRUNCATED_OUTPUT);
                String responderText = mode == Mode.WRAPPED_OUTPUT
                        ? "Tentu, berikut hasilnya:\n```json\n" + body + "\n```\nSemoga membantu!"
                        : body;
                return new AiResponse(responderText, tokens(request.userPrompt()), tokens(responderText), modelName(request));
            }
        }

        AutomationTask task = promptBuilder.extractTask(request.userPrompt());
        String json = toJson(buildOutput(task));
        String text = mode == Mode.WRAPPED_OUTPUT
                ? "Tentu, berikut hasilnya:\n```json\n" + json + "\n```\nSemoga membantu!"
                : json;
        return new AiResponse(text, tokens(request.userPrompt()), tokens(text), modelName(request));
    }

    // ---------- Pembuat keluaran ----------

    private Map<String, Object> buildOutput(AutomationTask task) {
        AutomationFramework framework = AutomationFramework.valueOf(task.framework());
        AutomationLanguage language = AutomationLanguage.valueOf(task.language());
        AutomationPattern pattern = AutomationPattern.valueOf(task.pattern());

        List<AutomationFileDTO> files = new ArrayList<>();
        if (pattern == AutomationPattern.PAGE_OBJECT_MODEL) {
            files.add(basePage(framework, language));
        }
        Set<String> usedSlugs = new HashSet<>();
        List<AutomationTask.TestCaseSpec> testCases = task.testCases() == null ? List.of() : task.testCases();
        for (int i = 0; i < testCases.size(); i++) {
            files.add(testFile(framework, language, pattern, testCases.get(i), i + 1, usedSlugs));
        }

        Map<String, Object> output = new LinkedHashMap<>();
        List<Map<String, String>> fileMaps = new ArrayList<>();
        for (AutomationFileDTO file : files) {
            fileMaps.add(Map.of("path", file.path(), "content", file.content()));
        }
        if (mode == Mode.EXTRA_FILES) {
            fileMaps.add(Map.of("path", ".gitignore", "content", "__pycache__/\nnode_modules/\n.env\n"));
            fileMaps.add(Map.of("path", "Dockerfile", "content", "FROM scratch\n"));
        }
        output.put("files", fileMaps);
        output.put("notes", BANNER + ". Kerangka kode contoh untuk " + framework.displayName() + " + " + language.displayName() + ".");
        return output;
    }

    private AutomationFileDTO testFile(
            AutomationFramework framework, AutomationLanguage language, AutomationPattern pattern,
            AutomationTask.TestCaseSpec testCase, int number, Set<String> usedSlugs
    ) {
        String title = testCase.title() == null ? "Test case " + number : testCase.title();
        String slug = slug(title, number);
        if (!usedSlugs.add(slug)) {
            slug = slug + "-" + number;
            usedSlugs.add(slug);
        }
        String snake = slug.replace('-', '_');
        String pascal = pascal(slug);
        boolean pom = pattern == AutomationPattern.PAGE_OBJECT_MODEL;

        List<String> stepLines = new ArrayList<>();
        stepLines.add("Priority: " + nullToDash(testCase.priority()) + ", type: " + nullToDash(testCase.type()));
        if (testCase.precondition() != null) {
            stepLines.add("Precondition: " + testCase.precondition());
        }
        List<AutomationTask.StepSpec> steps = testCase.steps() == null ? List.of() : testCase.steps();
        for (int i = 0; i < steps.size(); i++) {
            AutomationTask.StepSpec step = steps.get(i);
            stepLines.add("Step " + (i + 1) + ": " + nullToDash(step.action())
                    + (step.expected() == null || step.expected().isBlank() ? "" : " -> expected: " + step.expected()));
        }

        return switch (framework) {
            case PLAYWRIGHT -> playwright(language, title, slug, snake, pascal, pom, stepLines);
            case CYPRESS -> cypress(language, title, slug, stepLines);
            case SELENIUM -> selenium(language, title, slug, snake, pascal, pom, stepLines);
        };
    }

    // ---- Playwright ----
    private AutomationFileDTO playwright(AutomationLanguage language, String title, String slug, String snake,
                                         String pascal, boolean pom, List<String> stepLines) {
        switch (language) {
            case TYPESCRIPT, JAVASCRIPT -> {
                boolean ts = language == AutomationLanguage.TYPESCRIPT;
                String ext = ts ? "ts" : "js";
                String header = "// " + BANNER + "\n"
                        + (ts ? "import { test, expect } from '@playwright/test';\n" : "const { test, expect } = require('@playwright/test');\n")
                        + (pom ? (ts ? "import { BasePage } from '../pages/BasePage';\n" : "const { BasePage } = require('../pages/BasePage');\n") : "")
                        + "\n";
                String body = "test('" + js(title) + "', async ({ page }) => {\n"
                        + (pom ? "  const basePage = new BasePage(page);\n  await basePage.open();\n"
                        : "  await page.goto(process.env.BASE_URL ?? 'http://localhost:3000');\n")
                        + comments(stepLines, "  // ")
                        + "  await expect(page).toHaveURL(/.*/); // TODO: ganti dengan assertion sebenarnya\n"
                        + "});\n";
                return new AutomationFileDTO("tests/" + slug + ".spec." + ext, header + body);
            }
            case PYTHON -> {
                String content = "# " + BANNER + "\nimport os\nimport re\n\nfrom playwright.sync_api import Page, expect\n"
                        + (pom ? "from pages.base_page import BasePage\n" : "")
                        + "\n\ndef test_" + snake + "(page: Page) -> None:\n"
                        + "    \"\"\"" + py(title) + "\"\"\"\n"
                        + (pom ? "    base_page = BasePage(page)\n    base_page.open()\n"
                        : "    page.goto(os.environ.get(\"BASE_URL\", \"http://localhost:3000\"))\n")
                        + comments(stepLines, "    # ")
                        + "    expect(page).to_have_url(re.compile(\".*\"))  # TODO: ganti dengan assertion sebenarnya\n";
                return new AutomationFileDTO("tests/test_" + snake + ".py", content);
            }
            default -> {
                String content = "// " + BANNER + "\npackage tests;\n\n"
                        + "import com.microsoft.playwright.Browser;\nimport com.microsoft.playwright.Page;\nimport com.microsoft.playwright.Playwright;\n"
                        + "import org.junit.jupiter.api.DisplayName;\nimport org.junit.jupiter.api.Test;\n"
                        + (pom ? "import pages.BasePage;\n" : "") + "\n"
                        + "class " + pascal + "Test {\n\n    @Test\n    @DisplayName(\"" + java(title) + "\")\n    void run() {\n"
                        + "        try (Playwright playwright = Playwright.create()) {\n"
                        + "            Browser browser = playwright.chromium().launch();\n"
                        + "            Page page = browser.newPage();\n"
                        + (pom ? "            new BasePage(page).open();\n"
                        : "            page.navigate(System.getenv().getOrDefault(\"BASE_URL\", \"http://localhost:3000\"));\n")
                        + comments(stepLines, "            // ")
                        + "            // TODO: ganti dengan assertion sebenarnya\n        }\n    }\n}\n";
                return new AutomationFileDTO("src/test/java/tests/" + pascal + "Test.java", content);
            }
        }
    }

    // ---- Cypress (hanya JavaScript/TypeScript) ----
    private AutomationFileDTO cypress(AutomationLanguage language, String title, String slug, List<String> stepLines) {
        String ext = language == AutomationLanguage.TYPESCRIPT ? "ts" : "js";
        String content = "// " + BANNER + "\n\n"
                + "describe('" + js(title) + "', () => {\n  it('" + js(title) + "', () => {\n"
                + "    cy.visit(Cypress.env('BASE_URL') || 'http://localhost:3000');\n"
                + comments(stepLines, "    // ")
                + "    cy.url().should('match', /.*/); // TODO: ganti dengan assertion sebenarnya\n  });\n});\n";
        return new AutomationFileDTO("cypress/e2e/" + slug + ".cy." + ext, content);
    }

    // ---- Selenium ----
    private AutomationFileDTO selenium(AutomationLanguage language, String title, String slug, String snake,
                                       String pascal, boolean pom, List<String> stepLines) {
        switch (language) {
            case TYPESCRIPT, JAVASCRIPT -> {
                boolean ts = language == AutomationLanguage.TYPESCRIPT;
                String content = "// " + BANNER + "\n"
                        + (ts ? "import { Builder, WebDriver } from 'selenium-webdriver';\n"
                        : "const { Builder } = require('selenium-webdriver');\n")
                        + (pom ? (ts ? "import { BasePage } from '../pages/BasePage';\n" : "const { BasePage } = require('../pages/BasePage');\n") : "")
                        + "\ndescribe('" + js(title) + "', () => {\n"
                        + "  let driver" + (ts ? ": WebDriver" : "") + ";\n\n"
                        + "  beforeEach(async () => { driver = await new Builder().forBrowser('chrome').build(); });\n"
                        + "  afterEach(async () => { await driver.quit(); });\n\n"
                        + "  it('" + js(title) + "', async () => {\n"
                        + (pom ? "    await new BasePage(driver).open();\n"
                        : "    await driver.get(process.env.BASE_URL ?? 'http://localhost:3000');\n")
                        + comments(stepLines, "    // ")
                        + "    // TODO: ganti dengan assertion sebenarnya\n  });\n});\n";
                return new AutomationFileDTO("tests/" + slug + ".test." + (ts ? "ts" : "js"), content);
            }
            case PYTHON -> {
                String content = "# " + BANNER + "\nimport os\n\nimport pytest\nfrom selenium import webdriver\n"
                        + (pom ? "from pages.base_page import BasePage\n" : "")
                        + "\n\n@pytest.fixture()\ndef driver():\n    driver = webdriver.Chrome()\n    yield driver\n    driver.quit()\n\n\n"
                        + "def test_" + snake + "(driver) -> None:\n    \"\"\"" + py(title) + "\"\"\"\n"
                        + (pom ? "    BasePage(driver).open()\n"
                        : "    driver.get(os.environ.get(\"BASE_URL\", \"http://localhost:3000\"))\n")
                        + comments(stepLines, "    # ")
                        + "    # TODO: ganti dengan assertion sebenarnya\n";
                return new AutomationFileDTO("tests/test_" + snake + ".py", content);
            }
            default -> {
                String content = "// " + BANNER + "\npackage tests;\n\n"
                        + "import org.junit.jupiter.api.AfterEach;\nimport org.junit.jupiter.api.BeforeEach;\nimport org.junit.jupiter.api.DisplayName;\nimport org.junit.jupiter.api.Test;\n"
                        + "import org.openqa.selenium.WebDriver;\nimport org.openqa.selenium.chrome.ChromeDriver;\n"
                        + (pom ? "import pages.BasePage;\n" : "") + "\n"
                        + "class " + pascal + "Test {\n\n    private WebDriver driver;\n\n"
                        + "    @BeforeEach\n    void setUp() {\n        driver = new ChromeDriver();\n    }\n\n"
                        + "    @AfterEach\n    void tearDown() {\n        driver.quit();\n    }\n\n"
                        + "    @Test\n    @DisplayName(\"" + java(title) + "\")\n    void run() {\n"
                        + (pom ? "        new BasePage(driver).open();\n"
                        : "        driver.get(System.getenv().getOrDefault(\"BASE_URL\", \"http://localhost:3000\"));\n")
                        + comments(stepLines, "        // ")
                        + "        // TODO: ganti dengan assertion sebenarnya\n    }\n}\n";
                return new AutomationFileDTO("src/test/java/tests/" + pascal + "Test.java", content);
            }
        }
    }

    // ---- Page object dasar (pola PAGE_OBJECT_MODEL) ----
    private AutomationFileDTO basePage(AutomationFramework framework, AutomationLanguage language) {
        String banner = BANNER;
        return switch (language) {
            case TYPESCRIPT, JAVASCRIPT -> {
                boolean ts = language == AutomationLanguage.TYPESCRIPT;
                String ext = ts ? "ts" : "js";
                String type = framework == AutomationFramework.PLAYWRIGHT ? "Page" : "WebDriver";
                String importLine = ts
                        ? (framework == AutomationFramework.PLAYWRIGHT ? "import type { Page } from '@playwright/test';\n" : "import { WebDriver } from 'selenium-webdriver';\n")
                        : "";
                String open = framework == AutomationFramework.PLAYWRIGHT
                        ? "await this.page.goto(process.env.BASE_URL ?? 'http://localhost:3000');"
                        : "await this.page.get(process.env.BASE_URL ?? 'http://localhost:3000');";
                String content = "// " + banner + "\n" + importLine + "\n"
                        + (ts ? "export class BasePage {\n  constructor(protected readonly page: " + type + ") {}\n\n"
                        : "class BasePage {\n  constructor(page) {\n    this.page = page;\n  }\n\n")
                        + "  async open()" + (ts ? ": Promise<void>" : "") + " {\n    " + open + "\n  }\n}\n"
                        + (ts ? "" : "\nmodule.exports = { BasePage };\n");
                yield new AutomationFileDTO("pages/BasePage." + ext, content);
            }
            case PYTHON -> {
                String open = framework == AutomationFramework.PLAYWRIGHT
                        ? "self.page.goto(os.environ.get(\"BASE_URL\", \"http://localhost:3000\"))"
                        : "self.page.get(os.environ.get(\"BASE_URL\", \"http://localhost:3000\"))";
                yield new AutomationFileDTO("pages/base_page.py", "# " + banner + "\nimport os\n\n\nclass BasePage:\n"
                        + "    def __init__(self, page) -> None:\n        self.page = page\n\n"
                        + "    def open(self) -> None:\n        " + open + "\n");
            }
            default -> {
                String pageType = framework == AutomationFramework.PLAYWRIGHT ? "com.microsoft.playwright.Page" : "org.openqa.selenium.WebDriver";
                String open = framework == AutomationFramework.PLAYWRIGHT
                        ? "page.navigate(System.getenv().getOrDefault(\"BASE_URL\", \"http://localhost:3000\"));"
                        : "page.get(System.getenv().getOrDefault(\"BASE_URL\", \"http://localhost:3000\"));";
                yield new AutomationFileDTO("src/test/java/pages/BasePage.java", "// " + banner + "\npackage pages;\n\n"
                        + "public class BasePage {\n\n    protected final " + pageType + " page;\n\n"
                        + "    public BasePage(" + pageType + " page) {\n        this.page = page;\n    }\n\n"
                        + "    public void open() {\n        " + open + "\n    }\n}\n");
            }
        };
    }

    // ---------- Helper ----------

    private static String comments(List<String> lines, String prefix) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(prefix).append(oneLine(line)).append('\n');
        }
        return out.toString();
    }

    /** Satu baris, tanpa pemisah baris: teks test case tidak boleh keluar dari komentar dan merusak kode. */
    private static String oneLine(String text) {
        return text.replaceAll("[\\r\\n\\u2028\\u2029]+", " ").strip();
    }

    private static String js(String text) {
        return oneLine(text).replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String py(String text) {
        return oneLine(text).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String java(String text) {
        return py(text);
    }

    private static String nullToDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    static String slug(String title, int number) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "test-case-" + number : slug;
    }

    static String pascal(String slug) {
        StringBuilder out = new StringBuilder();
        for (String word : slug.split("-")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        String result = out.toString();
        // Nama kelas Java tidak boleh diawali angka.
        return result.isEmpty() || Character.isDigit(result.charAt(0)) ? "Tc" + result : result;
    }

    private String toJson(Map<String, Object> output) {
        try {
            return mapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal membuat keluaran palsu", e);
        }
    }

    private static int tokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    private static String modelName(AiRequest request) {
        return request.model() == null || request.model().isBlank() ? "fake" : request.model();
    }

    private void pause() {
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "FAKE: dihentikan saat menunggu");
            }
        }
    }
}

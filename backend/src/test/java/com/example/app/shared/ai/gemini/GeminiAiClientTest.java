// filepath: /backend/src/test/java/com/example/app/shared/ai/gemini/GeminiAiClientTest.java
package com.example.app.shared.ai.gemini;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiProviderException.Kind;
import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menguji adapter terhadap server HTTP palsu yang meniru Gemini -- TANPA API key sungguhan dan tanpa jaringan keluar.
 * Key yang dipakai di sini ("AQ.key-uji-bukan-asli") sengaja berformat baru Google dan BUKAN key nyata.
 */
class GeminiAiClientTest {

    private static final String KEY = "AQ.key-uji-bukan-asli";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Server Gemini palsu: merekam permintaan terakhir dan membalas sesuai yang diatur. */
    private static final class FakeGemini implements AutoCloseable {
        final HttpServer server;
        final AtomicReference<String> path = new AtomicReference<>();
        final AtomicReference<String> query = new AtomicReference<>();
        final AtomicReference<String> keyHeader = new AtomicReference<>();
        final AtomicReference<String> body = new AtomicReference<>();
        volatile int status = 200;
        volatile String responseBody = "{}";
        volatile long delayMillis = 0;

        FakeGemini() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                query.set(exchange.getRequestURI().getQuery());
                keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (IOException ignored) {
                    // klien sudah menyerah (uji timeout)
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        }

        JsonNode sentBody() throws IOException {
            return MAPPER.readTree(body.get());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static AiProperties props(String baseUrl) {
        AiProperties properties = new AiProperties();
        properties.setProvider("gemini");
        properties.setApiKey(KEY);
        properties.setBaseUrl(baseUrl);
        return properties;
    }

    private static AiRequest request() {
        return new AiRequest("Anda penguji.", "Buat 3 test case.", "gemini-model-uji", 777, Duration.ofSeconds(5));
    }

    private static String ok(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":11,\"candidatesTokenCount\":22,\"thoughtsTokenCount\":5}}";
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String error(int code, String status, String message) {
        return "{\"error\":{\"code\":" + code + ",\"status\":\"" + status + "\",\"message\":" + quote(message) + "}}";
    }

    private AiProviderException failure(FakeGemini gemini, int status, String body) {
        gemini.status = status;
        gemini.responseBody = body;
        return assertThrows(AiProviderException.class, () -> new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request()));
    }

    static final class LogCapture implements AutoCloseable {
        private final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GeminiAiClient.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        LogCapture() {
            appender.start();
            logger.addAppender(appender);
        }

        List<String> errorMessages() {
            return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).map(ILoggingEvent::getFormattedMessage).toList();
        }

        List<String> allMessages() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
        }
    }

    // ================= Permintaan yang dikirim =================

    @Test
    void sendsTheKeyOnlyInTheHeaderToTheGenerateContentEndpoint() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = ok("halo");

            AiResponse response = new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request());

            assertEquals("/v1beta/models/gemini-model-uji:generateContent", gemini.path.get());
            assertEquals(KEY, gemini.keyHeader.get());
            assertNull(gemini.query.get(), "key tidak boleh ada di URL/query");
            assertFalse(gemini.path.get().contains("AQ."));
            assertEquals("halo", response.text());
        }
    }

    @Test
    void theNewAqKeyFormatIsAcceptedBecauseTheKeyShapeIsNeverValidated() {
        for (String key : List.of("AQ.abc", "AIzaSyLama", "format-apa-saja-123")) {
            AiProperties properties = props("http://127.0.0.1:1");
            properties.setApiKey(key);
            // Tidak melempar karena "bentuk key salah": kegagalan hanya karena server tidak ada (UNAVAILABLE), bukan validasi key.
            AiProviderException e = assertThrows(AiProviderException.class,
                    () -> new GeminiAiClient(properties, MAPPER).generate(request()));
            assertEquals(Kind.UNAVAILABLE, e.getKind());
            assertFalse(e.getMessage().toLowerCase().contains("format"));
        }
    }

    @Test
    void buildsTheRequestBodyWithSystemInstructionUserContentAndTokenLimit() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = ok("x");
            new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request());

            JsonNode body = gemini.sentBody();
            assertEquals("Anda penguji.", body.at("/systemInstruction/parts/0/text").asText());
            assertEquals("user", body.at("/contents/0/role").asText());
            assertEquals("Buat 3 test case.", body.at("/contents/0/parts/0/text").asText());
            assertEquals(777, body.at("/generationConfig/maxOutputTokens").asInt());
            // bawaan: tidak ada mode JSON, suhu, maupun thinking yang dikirim
            assertTrue(body.at("/generationConfig/responseMimeType").isMissingNode());
            assertTrue(body.at("/generationConfig/temperature").isMissingNode());
            assertTrue(body.at("/generationConfig/thinkingConfig").isMissingNode());
        }
    }

    @Test
    void jsonModeAndOptionalSettingsAreSentOnlyWhenRequestedOrConfigured() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = ok("{}");
            AiProperties properties = props(gemini.baseUrl());
            properties.getGemini().setTemperature(0.3);
            properties.getGemini().setThinkingBudget(0);
            AiRequest json = new AiRequest("s", "u", "gemini-model-uji", 100, Duration.ofSeconds(5), true, null);

            new GeminiAiClient(properties, MAPPER).generate(json);

            JsonNode config = gemini.sentBody().path("generationConfig");
            assertEquals("application/json", config.path("responseMimeType").asText());
            assertEquals(0.3, config.path("temperature").asDouble());
            assertEquals(0, config.at("/thinkingConfig/thinkingBudget").asInt());
            assertTrue(config.path("responseSchema").isMissingNode() && config.path("responseJsonSchema").isMissingNode());
        }
    }

    @Test
    void theResponseSchemaIsMappedPerConfiguredModeAndOffByDefault() throws Exception {
        JsonNode schema = MAPPER.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":"
                + "{\"title\":{\"type\":\"string\"},\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}}}}");
        AiRequest withSchema = new AiRequest("s", "u", "gemini-model-uji", 100, Duration.ofSeconds(5), true, schema);

        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = ok("{}");

            // off (bawaan): mode JSON saja, skema TIDAK dikirim (parameter belum terverifikasi)
            new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(withSchema);
            JsonNode off = gemini.sentBody().path("generationConfig");
            assertEquals("application/json", off.path("responseMimeType").asText());
            assertTrue(off.path("responseSchema").isMissingNode() && off.path("responseJsonSchema").isMissingNode());

            // response-schema: format OpenAPI (tipe huruf besar, tanpa additionalProperties)
            AiProperties openApi = props(gemini.baseUrl());
            openApi.getGemini().setResponseSchema("response-schema");
            new GeminiAiClient(openApi, MAPPER).generate(withSchema);
            JsonNode converted = gemini.sentBody().at("/generationConfig/responseSchema");
            assertEquals("OBJECT", converted.path("type").asText());
            assertEquals("STRING", converted.at("/properties/title/type").asText());
            assertEquals("ARRAY", converted.at("/properties/tags/type").asText());
            assertEquals("STRING", converted.at("/properties/tags/items/type").asText());
            assertEquals("B", converted.at("/properties/tags/items/enum/1").asText());
            assertTrue(converted.path("additionalProperties").isMissingNode());

            // response-json-schema: JSON Schema apa adanya
            AiProperties standard = props(gemini.baseUrl());
            standard.getGemini().setResponseSchema("response-json-schema");
            new GeminiAiClient(standard, MAPPER).generate(withSchema);
            JsonNode raw = gemini.sentBody().at("/generationConfig/responseJsonSchema");
            assertEquals("object", raw.path("type").asText());
            assertFalse(raw.path("additionalProperties").asBoolean(true));
        }
        // skema asli tidak ikut berubah (konversi bekerja pada salinan)
        assertEquals("object", schema.path("type").asText());
    }

    @Test
    void usesTheDefaultGoogleEndpointWhenNoBaseUrlIsConfigured() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta", new GeminiAiClient(props(""), MAPPER).baseUrl());
        assertEquals("https://x.example/v1beta", new GeminiAiClient(props("https://x.example/v1beta/"), MAPPER).baseUrl());
    }

    // ================= Respons sukses =================

    @Test
    void readsTextAndCountsThinkingTokensAsOutput() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = ok("jawaban");

            AiResponse response = new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request());

            assertEquals(11, response.inputTokens());
            assertEquals(27, response.outputTokens());   // 22 kandidat + 5 thinking (ditagih sbg output)
            assertEquals("gemini-model-uji", response.model());
        }
    }

    @Test
    void joinsMultiplePartsAndSkipsThoughtSummaries() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = "{\"candidates\":[{\"content\":{\"parts\":["
                    + "{\"text\":\"pikiran internal\",\"thought\":true},{\"text\":\"bagian 1 \"},{\"text\":\"bagian 2\"}]},\"finishReason\":\"STOP\"}]}";

            assertEquals("bagian 1 bagian 2", new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request()).text());
        }
    }

    @Test
    void aTruncatedAnswerIsStillReturnedSoTheCallerCanSalvageIt() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.responseBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"a\\\":[1,2\"}]},\"finishReason\":\"MAX_TOKENS\"}]}";

            assertEquals("{\"a\":[1,2", new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(request()).text());
        }
    }

    // ================= Kegagalan -> kategori internal =================

    @Test
    void dailyQuotaIs429QuotaExhaustedWhilePerMinuteAndUnclear429AreTransient() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            String daily = error(429, "RESOURCE_EXHAUSTED", "Quota exceeded for metric: generate_content_free_tier_requests, quotaId: GenerateRequestsPerDayPerProjectPerModel-FreeTier");
            String perMinute = error(429, "RESOURCE_EXHAUSTED", "Quota exceeded ... quotaId: GenerateRequestsPerMinutePerProjectPerModel-FreeTier");
            String unclear = error(429, "RESOURCE_EXHAUSTED", "Resource has been exhausted (e.g. check quota).");

            assertEquals(Kind.QUOTA_EXHAUSTED, failure(gemini, 429, daily).getKind());
            assertEquals(Kind.RATE_LIMITED, failure(gemini, 429, perMinute).getKind());
            assertEquals(Kind.RATE_LIMITED, failure(gemini, 429, unclear).getKind());
        }
    }

    @Test
    void serverErrorsAreUnavailableAndOtherClientErrorsAreRejected() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 500, error(500, "INTERNAL", "oops")).getKind());
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 503, error(503, "UNAVAILABLE", "The model is overloaded")).getKind());
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 504, "").getKind());
            assertEquals(Kind.REJECTED, failure(gemini, 400, error(400, "INVALID_ARGUMENT", "Unsupported parameter")).getKind());
            assertEquals(Kind.REJECTED, failure(gemini, 413, "").getKind());
            assertEquals(Kind.TIMEOUT, failure(gemini, 408, "").getKind());
        }
    }

    @Test
    void badKeyPermissionAndUnknownModelAreConfigurationProblemsLoggedLoudlyButShownAsUnavailable() throws Exception {
        try (FakeGemini gemini = new FakeGemini(); LogCapture log = new LogCapture()) {
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 400, error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.")).getKind());
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 401, error(401, "UNAUTHENTICATED", "Request had invalid authentication credentials.")).getKind());
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 403, error(403, "PERMISSION_DENIED", "API has not been used in project")).getKind());
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 404, error(404, "NOT_FOUND", "models/xyz is not found")).getKind());

            assertEquals(4, log.errorMessages().stream().filter(m -> m.contains("[AI-CONFIG]")).count());
        }
    }

    @Test
    void blockedOrEmptyAnswersAreRejected() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            assertEquals(Kind.REJECTED, failure(gemini, 200, "{\"candidates\":[],\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}").getKind());
            assertEquals(Kind.REJECTED, failure(gemini, 200, "{}").getKind());
            assertEquals(Kind.REJECTED, failure(gemini, 200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}").getKind());
            assertEquals(Kind.REJECTED, failure(gemini, 200, "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}").getKind());
        }
    }

    @Test
    void aSuccessStatusWithANonJsonBodyIsUnavailable() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            assertEquals(Kind.UNAVAILABLE, failure(gemini, 200, "<html>proxy error</html>").getKind());
        }
    }

    @Test
    void aSlowServerIsATimeoutAndAnUnreachableOneIsUnavailable() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            gemini.delayMillis = 1500;
            gemini.responseBody = ok("lambat");
            AiRequest quick = new AiRequest("s", "u", "gemini-model-uji", 10, Duration.ofMillis(400));

            AiProviderException timeout = assertThrows(AiProviderException.class,
                    () -> new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(quick));
            assertEquals(Kind.TIMEOUT, timeout.getKind());
        }
        AiProviderException refused = assertThrows(AiProviderException.class,
                () -> new GeminiAiClient(props("http://127.0.0.1:1/v1beta"), MAPPER).generate(request()));
        assertEquals(Kind.UNAVAILABLE, refused.getKind());
    }

    @Test
    void anEmptyKeyOrAnUnsafeModelNameNeverReachesTheNetwork() throws Exception {
        try (FakeGemini gemini = new FakeGemini()) {
            AiProperties noKey = props(gemini.baseUrl());
            noKey.setApiKey("  ");
            assertEquals(Kind.UNAVAILABLE, assertThrows(AiProviderException.class,
                    () -> new GeminiAiClient(noKey, MAPPER).generate(request())).getKind());

            for (String badModel : List.of("../../etc", "model?x=1", "a/b", "m odel", "")) {
                AiRequest bad = new AiRequest("s", "u", badModel, 10, Duration.ofSeconds(2));
                assertEquals(Kind.UNAVAILABLE, assertThrows(AiProviderException.class,
                        () -> new GeminiAiClient(props(gemini.baseUrl()), MAPPER).generate(bad)).getKind(), badModel);
            }
            assertNull(gemini.path.get(), "tidak boleh ada permintaan yang terkirim");
        }
    }

    // ================= Kunci tidak pernah bocor =================

    @Test
    void theKeyNeverAppearsInLogsOrExceptionMessagesEvenIfGoogleEchoesIt() throws Exception {
        try (FakeGemini gemini = new FakeGemini(); LogCapture log = new LogCapture()) {
            String echoed = error(400, "INVALID_ARGUMENT", "API key not valid: " + KEY + " tidak dikenal");

            AiProviderException e = failure(gemini, 400, echoed);

            assertFalse(e.getMessage().contains(KEY), e.getMessage());
            assertTrue(e.getMessage().contains("***"));
            for (String message : log.allMessages()) {
                assertFalse(message.contains(KEY), message);
            }
            assertTrue(log.errorMessages().size() >= 1);
        }
    }
}

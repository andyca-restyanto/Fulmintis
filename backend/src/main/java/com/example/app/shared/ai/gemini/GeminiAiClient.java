// filepath: /backend/src/main/java/com/example/app/shared/ai/gemini/GeminiAiClient.java
package com.example.app.shared.ai.gemini;

import com.example.app.shared.ai.AiClient;
import com.example.app.shared.ai.AiGeminiProperties;
import com.example.app.shared.ai.AiProperties;
import com.example.app.shared.ai.AiProviderException;
import com.example.app.shared.ai.AiRequest;
import com.example.app.shared.ai.AiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adapter Google Gemini (Generative Language API, REST generateContent). Aktif hanya bila app.ai.provider=gemini.
 * <ul>
 *   <li>Key HANYA di header {@code x-goog-api-key}: tidak pernah di URL (query) dan tidak pernah di log. Bentuk key TIDAK
 *       divalidasi -- Google kini menerbitkan key berawalan "AQ." selain "AIza" lama; yang memegang format adalah Google.</li>
 *   <li>SEMUA kegagalan diterjemahkan menjadi {@link AiProviderException} (service tidak pernah melihat format error Google).</li>
 *   <li>Kesalahan KONFIGURASI (key/model/izin salah) dicatat sebagai [AI-CONFIG] ERROR dan diperlakukan UNAVAILABLE: itu bukan
 *       salah user, dan user hanya melihat pesan umum.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "gemini")
public class GeminiAiClient implements AiClient {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    static final String API_KEY_HEADER = "x-goog-api-key";

    // Nama model masuk ke path URL: hanya karakter aman (mencegah path/URL injection lewat konfigurasi).
    private static final Pattern SAFE_MODEL = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION", "IMAGE_SAFETY");

    private final AiProperties properties;
    private final ObjectMapper mapper;

    // Satu HttpClient dipakai ulang (mahal dibuat); timeout BACA diatur per panggilan lewat request factory.
    // Sengaja java.net.http, BUKAN HttpURLConnection: pada POST bermode streaming, HttpURLConnection melempar
    // HttpRetryException saat isi respons 401 dibaca -- sehingga error kunci/otentikasi (401 UNAUTHENTICATED, yang paling mungkin
    // terjadi dgn key baru berawalan "AQ.") tampil seperti "server tidak terjangkau" dan menyembunyikan penyebab sebenarnya.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public GeminiAiClient(AiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public String providerId() {
        return "gemini";
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw configError("app.ai.api-key kosong", null);
        }
        if (request.model() == null || !SAFE_MODEL.matcher(request.model()).matches()) {
            throw configError("nama model tidak valid: '" + request.model() + "' (periksa app.ai.free.model / app.ai.vip.model)", apiKey);
        }

        URI uri = URI.create(baseUrl() + "/models/" + request.model() + ":generateContent");
        String payload;
        try {
            payload = mapper.writeValueAsString(buildBody(request));
        } catch (JsonProcessingException e) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Gagal menyusun permintaan Gemini", e);
        }

        RawResponse raw = post(uri, apiKey.trim(), payload, request.timeout());
        if (raw.status / 100 != 2) {
            throw mapHttpError(raw.status, raw.body, apiKey);
        }
        return parseSuccess(raw.body, request.model(), apiKey);
    }

    // ---------- Permintaan ----------

    String baseUrl() {
        String configured = properties.getBaseUrl();
        String base = configured == null || configured.isBlank() ? DEFAULT_BASE_URL : configured.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    ObjectNode buildBody(AiRequest request) {
        AiGeminiProperties gemini = properties.getGemini();
        ObjectNode root = mapper.createObjectNode();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemPrompt());
        }
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", request.userPrompt());

        ObjectNode config = root.putObject("generationConfig");
        config.put("maxOutputTokens", request.maxOutputTokens());
        if (gemini.getTemperature() != null) {
            config.put("temperature", gemini.getTemperature());
        }
        if (request.jsonOutput() || request.responseSchema() != null) {
            config.put("responseMimeType", "application/json");
            String mode = gemini.getResponseSchema() == null ? "off" : gemini.getResponseSchema().trim().toLowerCase(Locale.ROOT);
            if (request.responseSchema() != null && !mode.equals("off")) {
                if (mode.equals("response-schema")) {
                    config.set("responseSchema", toOpenApiSchema(request.responseSchema().deepCopy()));
                } else {
                    config.set("responseJsonSchema", request.responseSchema());
                }
            }
        }
        if (gemini.getThinkingBudget() != null) {
            config.putObject("thinkingConfig").put("thinkingBudget", gemini.getThinkingBudget());
        }
        return root;
    }

    /** JSON Schema standar -> subset OpenAPI yang dipakai field responseSchema: tipe huruf besar, tanpa additionalProperties/$schema. */
    static JsonNode toOpenApiSchema(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove("additionalProperties");
            object.remove("$schema");
            if (object.has("type") && object.get("type").isTextual()) {
                object.put("type", object.get("type").asText().toUpperCase(Locale.ROOT));
            }
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                toOpenApiSchema(fields.next().getValue());
            }
        } else if (node.isArray()) {
            ((ArrayNode) node).forEach(GeminiAiClient::toOpenApiSchema);
        }
        return node;
    }

    private record RawResponse(int status, String body) {
    }

    private RawResponse post(URI uri, String apiKey, String payload, Duration timeout) {
        Duration readTimeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        RestClient client = RestClient.builder().requestFactory(factory).build();
        try {
            return client.post()
                    .uri(uri)
                    .header(API_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            // Tiga bentuk timeout: soket (HttpURLConnection), java.net.http (koneksi), dan TimeoutException yang dilempar Spring
            // pada timeout baca java.net.http.
            if (hasCause(e, SocketTimeoutException.class) || hasCause(e, HttpTimeoutException.class)
                    || hasCause(e, java.util.concurrent.TimeoutException.class)) {
                throw new AiProviderException(AiProviderException.Kind.TIMEOUT, "Gemini tidak membalas dalam " + readTimeout.toSeconds() + " detik", e);
            }
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Gemini tidak dapat dihubungi: " + e.getClass().getSimpleName(), e);
        } catch (RestClientException e) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Kegagalan HTTP ke Gemini: " + e.getClass().getSimpleName(), e);
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Respons sukses ----------

    private AiResponse parseSuccess(String body, String model, String apiKey) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Respons Gemini bukan JSON", e);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            throw new AiProviderException(AiProviderException.Kind.REJECTED,
                    "Gemini tidak mengembalikan kandidat" + (blockReason.isEmpty() ? "" : " (blockReason=" + blockReason + ")"));
        }

        JsonNode candidate = candidates.get(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue; // ringkasan pemikiran bukan bagian jawaban
            }
            if (part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        String finishReason = candidate.path("finishReason").asText("");
        if (text.length() == 0) {
            Kind kind = BLOCKED_FINISH_REASONS.contains(finishReason) ? Kind.BLOCKED : Kind.EMPTY;
            throw new AiProviderException(AiProviderException.Kind.REJECTED,
                    "Gemini tidak mengembalikan teks (finishReason=" + (finishReason.isEmpty() ? "-" : finishReason) + ", " + kind + ")");
        }

        JsonNode usage = root.path("usageMetadata");
        int input = usage.path("promptTokenCount").asInt(0);
        // Token thinking ditagih sebagai OUTPUT, jadi dijumlahkan ke output.
        int output = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
        return new AiResponse(text.toString(), input, output, model);
    }

    private enum Kind { BLOCKED, EMPTY }

    // ---------- Error ----------

    AiProviderException mapHttpError(int status, String body, String apiKey) {
        String googleStatus = "";
        String message = "";
        try {
            JsonNode error = mapper.readTree(body).path("error");
            googleStatus = error.path("status").asText("");
            message = error.path("message").asText("");
        } catch (IOException ignored) {
            message = body == null ? "" : body;
        }
        String detail = "Gemini HTTP " + status + (googleStatus.isEmpty() ? "" : " " + googleStatus) + ": " + sanitize(message, apiKey);
        String haystack = ((body == null ? "" : body) + " " + message).toLowerCase(Locale.ROOT);

        if (status == 429) {
            // Kuota HARIAN habis -> QUOTA_EXHAUSTED (circuit breaker menahan panggilan); batas per menit/sementara -> RATE_LIMITED.
            // Pembedaan memakai isi pesan Google (quotaId memuat "PerDay"/"PerMinute"); 429 yang tidak jelas dianggap sementara.
            boolean daily = haystack.contains("perday") || haystack.contains("per day") || haystack.contains("daily");
            return new AiProviderException(daily ? AiProviderException.Kind.QUOTA_EXHAUSTED : AiProviderException.Kind.RATE_LIMITED, detail);
        }
        if (status >= 500) {
            return new AiProviderException(AiProviderException.Kind.UNAVAILABLE, detail);
        }
        if (status == 408) {
            return new AiProviderException(AiProviderException.Kind.TIMEOUT, detail);
        }
        if (status == 400) {
            boolean badKey = haystack.contains("api_key_invalid") || haystack.contains("api key not valid") || haystack.contains("api key expired");
            return badKey ? configError(detail, apiKey) : new AiProviderException(AiProviderException.Kind.REJECTED, detail);
        }
        if (status == 401 || status == 403 || status == 404) {
            // Key salah/tidak punya izin/model tidak ada: kesalahan konfigurasi, bukan salah user.
            return configError(detail, apiKey);
        }
        return new AiProviderException(AiProviderException.Kind.REJECTED, detail);
    }

    private AiProviderException configError(String detail, String apiKey) {
        log.error("[AI-CONFIG] Konfigurasi Gemini bermasalah -- periksa key, izin API, dan nama model. Detail: {}", sanitize(detail, apiKey));
        return new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Konfigurasi Gemini bermasalah: " + sanitize(detail, apiKey));
    }

    /** Satu baris, tanpa key (kalau Google mengutipnya), dipotong 300 karakter. */
    static String sanitize(String text, String apiKey) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[\\r\\n]+", " ");
        if (apiKey != null && !apiKey.isBlank()) {
            clean = clean.replace(apiKey, "***");
        }
        return clean.length() > 300 ? clean.substring(0, 300) + "..." : clean;
    }
}

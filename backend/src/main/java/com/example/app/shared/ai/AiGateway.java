// filepath: /backend/src/main/java/com/example/app/shared/ai/AiGateway.java
package com.example.app.shared.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Pintu tunggal ke AI untuk seluruh aplikasi: memilih adapter dari
 * {@code app.ai.provider}, menerapkan konfigurasi per tier, dan menahan panggilan
 * lewat circuit breaker saat kuota provider habis.
 */
@Slf4j
@Service
public class AiGateway {

    private final AiProperties properties;
    private final AiClient client; // null kalau provider kosong
    private final AiCircuitBreaker breaker;

    /** Dipakai Spring. ObjectProvider: aman walau TIDAK ADA bean AiClient sama sekali (provider kosong). */
    @Autowired
    public AiGateway(AiProperties properties, ObjectProvider<AiClient> clientProvider, Clock clock) {
        this(properties, clientProvider.orderedStream().toList(), clock);
    }

    public AiGateway(AiProperties properties, List<AiClient> clients, Clock clock) {
        this.properties = properties;
        this.client = properties.isConfigured()
                ? clients.stream().filter(c -> c.providerId().equalsIgnoreCase(properties.getProvider())).findFirst().orElse(null)
                : null;
        this.breaker = new AiCircuitBreaker(clock);
    }

    /** Provider sudah dipilih dan adapter-nya ada (statis menurut konfigurasi). */
    public boolean isEnabled() {
        return client != null;
    }

    /** Siap menerima permintaan SEKARANG: aktif dan circuit breaker tidak sedang terbuka. */
    public boolean canAcceptRequests() {
        return client != null && !breaker.isOpen();
    }

    public AiTierProperties tier(AiTier tier) {
        return properties.tier(tier);
    }

    /**
     * @throws AiProviderException semua kegagalan (tidak dikonfigurasi, circuit terbuka, atau
     *                             kegagalan provider yang sudah diterjemahkan adapter)
     */
    public AiResponse generate(AiTier tier, String systemPrompt, String userPrompt) {
        AiTierProperties settings = properties.tier(tier);
        return generate(tier, new AiCallSpec(systemPrompt, userPrompt, settings.getMaxOutputTokens(),
                Duration.ofSeconds(settings.getTimeoutSeconds()), false, null));
    }

    /** Sama dengan di atas, tetapi batas keluaran, timeout, dan mode JSON ditentukan fitur pemanggil. */
    public AiResponse generate(AiTier tier, AiCallSpec spec) {
        if (client == null) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    "Provider AI belum dikonfigurasi (app.ai.provider kosong).");
        }
        if (breaker.isOpen()) {
            throw new AiProviderException(AiProviderException.Kind.QUOTA_EXHAUSTED,
                    "Panggilan ditahan: provider baru saja melaporkan kuota/saldo habis (circuit breaker terbuka).");
        }

        AiTierProperties settings = properties.tier(tier);
        AiRequest request = new AiRequest(spec.systemPrompt(), spec.userPrompt(), settings.getModel(),
                spec.maxOutputTokens(), spec.timeout(), spec.jsonOutput(), spec.responseSchema());

        try {
            return client.generate(request);
        } catch (AiProviderException e) {
            if (e.getKind() == AiProviderException.Kind.QUOTA_EXHAUSTED
                    && breaker.trip(Duration.ofMinutes(properties.getQuotaCircuitMinutes()))) {
                // Dicatat SEKALI per jendela circuit. User hanya melihat pesan umum, jadi baris
                // ini satu-satunya tanda bahwa kuota/saldo provider habis -- perlu tindakan admin.
                log.error("[AI-ALERT] Kuota/saldo penyedia AI HABIS. Panggilan generate ditahan selama {} menit. "
                                + "Perlu tindakan admin (isi ulang kuota/saldo provider). Detail: {}",
                        properties.getQuotaCircuitMinutes(), e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            // Adapter tidak boleh melempar exception mentah; kalau terjadi, jangan bocorkan -- bungkus.
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    "Kesalahan tak terduga pada klien AI: " + e.getClass().getSimpleName(), e);
        }
    }
}

// filepath: /backend/src/main/java/com/example/app/shared/ai/AiProviderException.java
package com.example.app.shared.ai;

/**
 * SATU-SATUNYA bentuk kegagalan yang boleh keluar dari adapter provider.
 * Adapter bertugas menerjemahkan error/format respons provider-nya sendiri
 * menjadi salah satu {@link Kind} di bawah; service tidak pernah melihat
 * format error asli provider.
 * <p>
 * {@code getMessage()} adalah detail INTERNAL (untuk log) dan TIDAK PERNAH
 * ditampilkan ke user.
 */
public class AiProviderException extends RuntimeException {

    public enum Kind {
        /** Kuota/saldo/billing provider habis (mis. HTTP 402, atau 429 jenis "insufficient quota"). */
        QUOTA_EXHAUSTED,
        /** Terlalu banyak permintaan sesaat (rate limit sementara). */
        RATE_LIMITED,
        /** Provider error/tidak bisa dihubungi (5xx, koneksi gagal, tidak dikonfigurasi). */
        UNAVAILABLE,
        /** Melewati batas waktu. */
        TIMEOUT,
        /** Permintaan ditolak provider (mis. 400/403, konten ditolak, model tidak ada). */
        REJECTED
    }

    private final Kind kind;

    public AiProviderException(Kind kind, String internalDetail) {
        super(internalDetail);
        this.kind = kind;
    }

    public AiProviderException(Kind kind, String internalDetail, Throwable cause) {
        super(internalDetail, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}

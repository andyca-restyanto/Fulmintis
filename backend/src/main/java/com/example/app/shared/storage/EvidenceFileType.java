// filepath: /backend/src/main/java/com/example/app/shared/storage/EvidenceFileType.java
package com.example.app.shared.storage;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Whitelist tipe file evidence + deteksi tipe dari ISI file (magic bytes).
 * <p>
 * KENAPA: header {@code Content-Type} dan nama file dikirim klien, jadi bisa
 * dipalsukan. File {@code x.html} yang dikirim dengan header {@code image/png}
 * dulu lolos dan tersimpan sebagai {@code uuid.html}; kalau lalu dibuka di
 * browser, skrip di dalamnya bisa jalan (XSS). Sekarang tipe & ekstensi
 * ditentukan HANYA dari byte awal file, dan hanya tipe di enum ini yang boleh.
 * SVG/HTML sengaja tidak ada di daftar.
 */
public enum EvidenceFileType {

    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp"),
    MP4("mp4", "video/mp4"),
    MOV("mov", "video/quicktime"),
    WEBM("webm", "video/webm");

    /** Jumlah byte awal yang cukup untuk semua signature di bawah. */
    public static final int HEADER_BYTES = 16;

    // Major brand ISO-BMFF yang dianggap MP4. Brand lain (mis. "heic", "avif")
    // sengaja TIDAK masuk supaya file gambar HEIC tidak menyamar jadi video.
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "msnv", "M4V "
    );

    private final String extension;
    private final String contentType;

    EvidenceFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** Deteksi dari byte awal file. Kosong kalau bukan salah satu tipe yang diizinkan. */
    public static Optional<EvidenceFileType> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        int n = header.length;

        if (n >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (n >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (n >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8'
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return Optional.of(GIF);
        }
        if (n >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return Optional.of(WEBP);
        }
        if (n >= 4 && (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) {
            return Optional.of(WEBM);
        }
        if (n >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            String brand = new String(header, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if ("qt  ".equals(brand)) {
                return Optional.of(MOV);
            }
            if (MP4_BRANDS.contains(brand)) {
                return Optional.of(MP4);
            }
        }
        return Optional.empty();
    }

    /**
     * Tipe dari EKSTENSI nama file yang tersimpan -- dipakai saat menyajikan
     * file. File lama (sebelum whitelist) yang ekstensinya tidak dikenal
     * (mis. .html/.svg) balas kosong -> caller wajib menyajikannya sebagai
     * download biner, bukan inline.
     */
    public static Optional<EvidenceFileType> fromStoredFileName(String storedFileName) {
        if (storedFileName == null) {
            return Optional.empty();
        }
        int dot = storedFileName.lastIndexOf('.');
        if (dot < 0 || dot == storedFileName.length() - 1) {
            return Optional.empty();
        }
        String ext = storedFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> Optional.of(PNG);
            case "jpg", "jpeg" -> Optional.of(JPEG);
            case "gif" -> Optional.of(GIF);
            case "webp" -> Optional.of(WEBP);
            case "mp4" -> Optional.of(MP4);
            case "mov" -> Optional.of(MOV);
            case "webm" -> Optional.of(WEBM);
            default -> Optional.empty();
        };
    }
}

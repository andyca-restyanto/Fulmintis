// filepath: /backend/src/test/java/com/example/app/shared/storage/EvidenceFileTypeTest.java
package com.example.app.shared.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceFileTypeTest {

    private static byte[] header(int... values) {
        byte[] bytes = new byte[EvidenceFileType.HEADER_BYTES];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] ascii(String text) {
        return Arrays.copyOf(text.getBytes(StandardCharsets.ISO_8859_1), EvidenceFileType.HEADER_BYTES);
    }

    @Test
    void detectsAllowedTypesFromMagicBytes() {
        assertEquals(EvidenceFileType.PNG,
                EvidenceFileType.detect(header(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)).orElseThrow());
        assertEquals(EvidenceFileType.JPEG, EvidenceFileType.detect(header(0xFF, 0xD8, 0xFF, 0xE0)).orElseThrow());
        assertEquals(EvidenceFileType.GIF, EvidenceFileType.detect(ascii("GIF89a")).orElseThrow());
        assertEquals(EvidenceFileType.WEBP,
                EvidenceFileType.detect(header('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P')).orElseThrow());
        assertEquals(EvidenceFileType.WEBM, EvidenceFileType.detect(header(0x1A, 0x45, 0xDF, 0xA3)).orElseThrow());
        assertEquals(EvidenceFileType.MP4,
                EvidenceFileType.detect(header(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm')).orElseThrow());
        assertEquals(EvidenceFileType.MOV,
                EvidenceFileType.detect(header(0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' ')).orElseThrow());
    }

    @Test
    void rejectsHtmlSvgAndOtherContainers() {
        assertTrue(EvidenceFileType.detect(ascii("<html><script>alert(1)</script>")).isEmpty());
        assertTrue(EvidenceFileType.detect(ascii("<svg xmlns=")).isEmpty());
        // RIFF tapi bukan WEBP (mis. WAV) dan ISO-BMFF berbrand gambar (HEIC)
        assertTrue(EvidenceFileType.detect(header('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E')).isEmpty());
        assertTrue(EvidenceFileType.detect(header(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')).isEmpty());
        assertTrue(EvidenceFileType.detect(new byte[0]).isEmpty());
    }

    @Test
    void storedFileNamesOutsideWhitelistAreNotServedInline() {
        assertTrue(EvidenceFileType.fromStoredFileName("legacy.html").isEmpty());
        assertTrue(EvidenceFileType.fromStoredFileName("legacy.svg").isEmpty());
        assertTrue(EvidenceFileType.fromStoredFileName("noextension").isEmpty());
        assertTrue(EvidenceFileType.fromStoredFileName(null).isEmpty());
        assertEquals(EvidenceFileType.PNG, EvidenceFileType.fromStoredFileName("a.PNG").orElseThrow());
    }
}

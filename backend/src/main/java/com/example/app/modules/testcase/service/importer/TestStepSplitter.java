// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/TestStepSplitter.java
package com.example.app.modules.testcase.service.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mengubah isi sel "Test step" / "Expected results" (satu langkah per baris di
 * dalam sel) menjadi teks bernomor yang SAMA dengan format yang disimpan UI
 * ("1. ...\n2. ...", lihat testStepSerializer.ts di FE), sehingga halaman
 * edit dan halaman eksekusi (tanda PASSED/FAILED per langkah berbasis urutan)
 * membacanya dengan benar.
 */
public final class TestStepSplitter {

    // Penomoran/pointer bawaan user di depan baris: "1.", "1)", "1 -", "1:", "-", "*", bullet.
    // Angka TANPA tanda baca setelahnya ("2 apel") bukan penomoran dan dipertahankan.
    private static final Pattern LEADING_MARKER = Pattern.compile("^\\s*(?:\\d+\\s*[.):\\-]|[-*\\u2022])\\s*");

    private TestStepSplitter() {
    }

    /** Pecah per baris, buang baris kosong dan penomoran bawaan. Sel kosong -> list kosong. */
    public static List<String> split(String cellText) {
        List<String> lines = new ArrayList<>();
        if (cellText == null) {
            return lines;
        }
        for (String rawLine : cellText.split("\\r?\\n|\\r")) {
            String line = LEADING_MARKER.matcher(rawLine.strip()).replaceFirst("").strip();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Nomori ulang jadi "1. a\n2. b". {@code size} > jumlah baris -> dilengkapi
     * baris kosong bernomor ("3. ") supaya sejajar dgn kolom pasangannya.
     * Mengembalikan null kalau {@code size} = 0 (tidak ada isi).
     */
    public static String number(List<String> lines, int size) {
        if (size <= 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(i + 1).append(". ").append(i < lines.size() ? lines.get(i) : "");
        }
        return builder.toString();
    }
}

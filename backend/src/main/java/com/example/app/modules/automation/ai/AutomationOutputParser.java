// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/AutomationOutputParser.java
package com.example.app.modules.automation.ai;

import com.example.app.modules.automation.dto.AutomationFileDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membaca & memvalidasi keluaran teks AI. Tahan terhadap JSON yang dibungkus blok kode markdown atau
 * kalimat pengantar (provider tanpa mode JSON terstruktur sering begitu), tetapi KERAS terhadap isinya:
 * path berbahaya, ekstensi tak diizinkan, duplikat, atau ukuran berlebih menolak SELURUH keluaran
 * (path ganjil menandakan manipulasi, jadi tidak ditambal diam-diam).
 */
@Component
public class AutomationOutputParser {

    public static final int MAX_FILES = 30;
    public static final int MAX_FILE_CHARS = 200_000;
    public static final int MAX_TOTAL_CHARS = 500_000;
    private static final int MAX_NOTES_CHARS = 2000;

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");

    private final ObjectMapper mapper;

    public AutomationOutputParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ParsedOutput parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAiOutputException("keluaran kosong");
        }

        String json = extractJsonObject(raw);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidAiOutputException("bukan JSON valid: " + e.getOriginalMessage());
        }

        JsonNode filesNode = root.get("files");
        if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
            throw new InvalidAiOutputException("field 'files' tidak ada atau kosong");
        }
        if (filesNode.size() > MAX_FILES) {
            throw new InvalidAiOutputException("terlalu banyak berkas: " + filesNode.size());
        }

        List<AutomationFileDTO> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long totalChars = 0;
        for (JsonNode fileNode : filesNode) {
            JsonNode pathNode = fileNode.get("path");
            JsonNode contentNode = fileNode.get("content");
            if (pathNode == null || !pathNode.isTextual() || contentNode == null || !contentNode.isTextual()) {
                throw new InvalidAiOutputException("setiap berkas harus punya 'path' dan 'content' bertipe teks");
            }
            String path = pathNode.asText().strip();
            GeneratedPaths.Verdict verdict = GeneratedPaths.classify(path);
            if (verdict == GeneratedPaths.Verdict.UNSAFE) {
                // Path berbahaya / berkas yang bisa dieksekusi: seluruh keluaran ditolak (bisa jadi tanda prompt injection).
                throw new InvalidAiOutputException("path ditolak '" + abbreviate(path) + "': " + GeneratedPaths.problemWith(path));
            }
            if (verdict == GeneratedPaths.Verdict.SKIPPABLE) {
                // Berkas pendamping yang wajar ditambahkan AI (.gitignore, Dockerfile, .env.example, ...): dilewati, bukan menggagalkan hasil.
                skipped.add(printable(path));
                continue;
            }
            if (!seen.add(path.toLowerCase(Locale.ROOT))) {
                skipped.add(printable(path) + " (ganda)");
                continue;
            }
            String content = contentNode.asText();
            if (content.length() > MAX_FILE_CHARS) {
                throw new InvalidAiOutputException("berkas terlalu besar: " + abbreviate(path));
            }
            totalChars += content.length();
            if (totalChars > MAX_TOTAL_CHARS) {
                throw new InvalidAiOutputException("total isi berkas terlalu besar");
            }
            files.add(new AutomationFileDTO(path, content));
        }

        if (files.isEmpty()) {
            throw new InvalidAiOutputException("tidak ada berkas yang dapat dipakai (semua dilewati: " + String.join(", ", skipped) + ")");
        }

        String notes = root.hasNonNull("notes") && root.get("notes").isTextual() ? root.get("notes").asText().strip() : null;
        return new ParsedOutput(files, combineNotes(notes, skipNote(skipped)), List.copyOf(skipped));
    }

    private static final int MAX_SKIPPED_LISTED = 10;

    /** Keterangan utk user tentang berkas yang tidak ikut dalam hasil (ditampilkan di catatan hasil generate). */
    private static String skipNote(List<String> skipped) {
        if (skipped.isEmpty()) {
            return null;
        }
        List<String> listed = skipped.subList(0, Math.min(MAX_SKIPPED_LISTED, skipped.size()));
        String more = skipped.size() > listed.size() ? ", dan " + (skipped.size() - listed.size()) + " lainnya" : "";
        return "Berkas yang tidak disertakan (jenis tidak didukung atau ganda): " + String.join(", ", listed) + more + ".";
    }

    /** Catatan AI dipotong bila perlu supaya keterangan berkas yang dilewati SELALU muat dalam batas panjang. */
    private static String combineNotes(String aiNotes, String skipNote) {
        String ai = aiNotes == null || aiNotes.isEmpty() ? null : aiNotes;
        if (skipNote == null) {
            return ai == null ? null : (ai.length() > MAX_NOTES_CHARS ? ai.substring(0, MAX_NOTES_CHARS) : ai);
        }
        int room = MAX_NOTES_CHARS - skipNote.length() - 2;
        String aiPart = ai == null || room <= 0 ? null : (ai.length() > room ? ai.substring(0, room) : ai);
        return aiPart == null ? skipNote : aiPart + "\n\n" + skipNote;
    }

    /** Path dari AI ditampilkan di catatan: karakter kontrol diganti supaya tidak bisa menyisipkan baris/format. */
    private static String printable(String path) {
        return abbreviate(path.replaceAll("\\p{Cntrl}", "?"));
    }

    /** Ambil objek JSON pertama yang seimbang: dari dalam blok kode ``` bila ada, kalau tidak dari teks mentah. */
    static String extractJsonObject(String raw) {
        String text = raw.strip();
        Matcher fenced = CODE_FENCE.matcher(text);
        if (fenced.find() && fenced.group(1).indexOf('{') >= 0) {
            text = fenced.group(1);
        }

        int start = text.indexOf('{');
        if (start < 0) {
            throw new InvalidAiOutputException("tidak ada objek JSON di keluaran");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new InvalidAiOutputException("objek JSON tidak lengkap (kemungkinan keluaran terpotong)");
    }

    private static String abbreviate(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "...";
    }
}

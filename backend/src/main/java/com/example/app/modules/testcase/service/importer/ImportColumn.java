// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/ImportColumn.java
package com.example.app.modules.testcase.service.importer;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Kolom template import + aturan auto-mapping header Excel -> field sistem.
 * Header dicocokkan tanpa membedakan huruf besar/kecil, spasi, tanda hubung,
 * garis bawah, dan tanda baca ("Pre-condition" = "precondition" = "PRE CONDITION"),
 * dan sinonim umum ikut dikenali. Urutan kolom di file bebas.
 */
public enum ImportColumn {

    TITLE("title", "Title", true, Set.of("title", "judul", "testcasetitle", "casetitle", "testcase")),
    PRIORITY("priority", "Priority", true, Set.of("priority", "prioritas")),
    TYPE("type", "Test type", true, Set.of("testtype", "type", "tipe", "tipetest", "testcasetype")),
    SCENARIO_TYPE("scenarioType", "Scenario type", false,
            Set.of("scenariotype", "scenario", "skenario", "tipeskenario")),
    DESCRIPTION("description", "Description", false, Set.of("description", "deskripsi")),
    OBJECTIVE("objective", "Objective", false, Set.of("objective", "objektif", "tujuan")),
    PRECONDITION("precondition", "Pre-condition", false,
            Set.of("precondition", "preconditions", "prakondisi", "prasyarat")),
    TEST_STEP("testStep", "Test step", false,
            Set.of("teststep", "teststeps", "step", "steps", "langkah", "langkahlangkah", "langkahtest")),
    EXPECTED_RESULT("expectedResult", "Expected results", false,
            Set.of("expectedresult", "expectedresults", "expected", "hasilyangdiharapkan", "hasildiharapkan"));

    private final String fieldName;
    private final String templateHeader;
    private final boolean required;
    private final Set<String> aliases;

    ImportColumn(String fieldName, String templateHeader, boolean required, Set<String> aliases) {
        this.fieldName = fieldName;
        this.templateHeader = templateHeader;
        this.required = required;
        this.aliases = aliases;
    }

    public String fieldName() {
        return fieldName;
    }

    /** Nama kolom persis seperti di template (dipakai di pesan error & header template). */
    public String templateHeader() {
        return templateHeader;
    }

    public boolean required() {
        return required;
    }

    /** Huruf kecil, hanya a-z dan 0-9 (spasi, tanda hubung, dan tanda baca dibuang). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Kolom sistem untuk sebuah header Excel, atau kosong kalau tidak dikenali. */
    public static Optional<ImportColumn> match(String header) {
        String normalized = normalize(header);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (ImportColumn column : values()) {
            if (column.aliases.contains(normalized)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }
}

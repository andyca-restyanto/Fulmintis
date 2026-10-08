// filepath: /backend/src/test/java/com/example/app/modules/testcase/service/TestStepSplitterAndColumnTest.java
package com.example.app.modules.testcase.service;

import com.example.app.modules.testcase.exception.InvalidImportFileException;
import com.example.app.modules.testcase.service.importer.ImportColumn;
import com.example.app.modules.testcase.service.importer.ImportFileGuard;
import com.example.app.modules.testcase.service.importer.TestStepSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStepSplitterAndColumnTest {

    // ---- TestStepSplitter ----

    @Test
    void splitsPerLineAndStripsExistingNumbering() {
        assertEquals(List.of("Buka halaman", "Isi form", "Klik Simpan"),
                TestStepSplitter.split("1. Buka halaman\n2) Isi form\r\n3 - Klik Simpan"));
        assertEquals(List.of("a", "b", "c"), TestStepSplitter.split("- a\n* b\n\u2022 c"));
    }

    @Test
    void keepsNumbersThatAreContentNotNumbering() {
        assertEquals(List.of("2 apel dimasukkan ke keranjang"), TestStepSplitter.split("2 apel dimasukkan ke keranjang"));
        assertEquals(List.of("Login sebagai user"), TestStepSplitter.split("10. Login sebagai user"));
    }

    @Test
    void dropsBlankLinesAndHandlesNull() {
        assertEquals(List.of("a", "b"), TestStepSplitter.split("a\n\n   \nb\n"));
        assertTrue(TestStepSplitter.split(null).isEmpty());
        assertTrue(TestStepSplitter.split("   ").isEmpty());
    }

    @Test
    void numbersLikeTheUiSerializerAndPadsToSize() {
        assertEquals("1. a\n2. b", TestStepSplitter.number(List.of("a", "b"), 2));
        // dilengkapi baris kosong bernomor supaya sejajar dgn kolom pasangannya
        assertEquals("1. a\n2. \n3. ", TestStepSplitter.number(List.of("a"), 3));
        assertNull(TestStepSplitter.number(List.of(), 0));
    }

    // ---- ImportColumn (auto-mapping header) ----

    @Test
    void matchesHeadersIgnoringCaseSpacesAndPunctuation() {
        assertEquals(ImportColumn.PRECONDITION, ImportColumn.match("Pre-condition").orElseThrow());
        assertEquals(ImportColumn.PRECONDITION, ImportColumn.match("PRE CONDITION").orElseThrow());
        assertEquals(ImportColumn.TYPE, ImportColumn.match("Test type").orElseThrow());
        assertEquals(ImportColumn.TYPE, ImportColumn.match("  test_type ").orElseThrow());
        assertEquals(ImportColumn.EXPECTED_RESULT, ImportColumn.match("Expected results").orElseThrow());
        assertEquals(ImportColumn.EXPECTED_RESULT, ImportColumn.match("expected result").orElseThrow());
        assertEquals(ImportColumn.SCENARIO_TYPE, ImportColumn.match("Scenario Type").orElseThrow());
        assertEquals(ImportColumn.TEST_STEP, ImportColumn.match("Test Steps").orElseThrow());
        assertEquals(ImportColumn.TITLE, ImportColumn.match("Judul").orElseThrow());
    }

    @Test
    void unknownOrBlankHeadersDoNotMatch() {
        assertTrue(ImportColumn.match("Owner").isEmpty());
        assertTrue(ImportColumn.match("   ").isEmpty());
        assertTrue(ImportColumn.match(null).isEmpty());
    }

    @Test
    void onlyTitlePriorityAndTestTypeAreRequired() {
        long required = java.util.Arrays.stream(ImportColumn.values()).filter(ImportColumn::required).count();
        assertEquals(3L, required);
        assertTrue(ImportColumn.TITLE.required() && ImportColumn.PRIORITY.required() && ImportColumn.TYPE.required());
    }

    // ---- ImportFileGuard ----

    private static final byte[] ZIP = {'P', 'K', 3, 4};

    @Test
    void guardAcceptsRealXlsxLookingFile() {
        ImportFileGuard.check("data.XLSX", 1000, ZIP); // tidak melempar
    }

    @Test
    void guardRejectsWrongExtensionSizeAndContent() {
        assertThrows(InvalidImportFileException.class, () -> ImportFileGuard.check("data.xls", 1000, ZIP));
        assertThrows(InvalidImportFileException.class, () -> ImportFileGuard.check("data.csv", 1000, ZIP));
        assertThrows(InvalidImportFileException.class, () -> ImportFileGuard.check(null, 1000, ZIP));
        assertThrows(InvalidImportFileException.class, () -> ImportFileGuard.check("data.xlsx", 0, ZIP));
        assertThrows(InvalidImportFileException.class,
                () -> ImportFileGuard.check("data.xlsx", ImportFileGuard.MAX_FILE_BYTES + 1, ZIP));
        // di-rename jadi .xlsx tapi isinya bukan ZIP (mis. HTML / .xls lama / teks)
        assertThrows(InvalidImportFileException.class,
                () -> ImportFileGuard.check("data.xlsx", 1000, "<html>".getBytes()));
        assertThrows(InvalidImportFileException.class,
                () -> ImportFileGuard.check("data.xlsx", 1000, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}));
    }
}

// filepath: /backend/src/main/java/com/example/app/modules/testcase/ai/ParsedDrafts.java
package com.example.app.modules.testcase.ai;

import com.example.app.modules.testcase.dto.TestCaseDraftDTO;

import java.util.List;

/**
 * @param truncated true = keluaran AI terpotong (batas token); hanya draft yang LENGKAP yang diselamatkan
 * @param discarded jumlah draft yang dibuang karena tidak valid / kembar / melebihi jumlah yang diminta
 */
public record ParsedDrafts(List<TestCaseDraftDTO> drafts, boolean truncated, int discarded) {
}

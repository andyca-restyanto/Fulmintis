// backend/src/main/java/com/example/app/modules/testcase/TestCaseStatus.java
package com.example.app.modules.testcase;

/**
 * Status lifecycle test case.
 * <p>
 * ACTIVE   -- default saat test case dibuat, tampil normal di listing/search
 *             utk OWNER maupun COLLABORATOR.
 * ARCHIVED -- hasil "delete" test case (requirement #2: delete = soft
 *             delete/flag, BUKAN hard delete dari database). Test case
 *             dengan status ini disembunyikan total dari COLLABORATOR
 *             (requirement #4) dan hanya bisa dilihat lewat endpoint
 *             khusus oleh OWNER project (lihat TestCaseController /
 *             TestCaseServiceImpl).
 */
public enum TestCaseStatus {
    ACTIVE,
    ARCHIVED
}

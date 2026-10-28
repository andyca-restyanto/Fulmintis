// backend/src/main/java/com/example/app/modules/testrepository/exception/TestFolderNotFoundException.java
package com.example.app.modules.testrepository.exception;

/**
 * Dilempar saat parentId yang dikirim user TIDAK VALID -- baik karena
 * folder-nya memang tidak ada, MAUPUN karena folder itu ada tapi milik
 * project LAIN (bukan project tujuan). Sengaja digabung jadi 1 pesan/404
 * (anti-enumeration, pola yang sama dengan ProjectNotFoundException) supaya
 * user tidak bisa "meraba-raba" folder-id milik project orang lain.
 */
public class TestFolderNotFoundException extends RuntimeException {
    public TestFolderNotFoundException() {
        super("Parent folder tidak ditemukan di project ini.");
    }
}

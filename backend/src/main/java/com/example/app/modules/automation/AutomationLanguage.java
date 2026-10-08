// filepath: /backend/src/main/java/com/example/app/modules/automation/AutomationLanguage.java
package com.example.app.modules.automation;

public enum AutomationLanguage {
    JAVA("Java", "java"),
    JAVASCRIPT("JavaScript", "js"),
    TYPESCRIPT("TypeScript", "ts"),
    PYTHON("Python", "py");

    private final String displayName;
    private final String sourceExtension;

    AutomationLanguage(String displayName, String sourceExtension) {
        this.displayName = displayName;
        this.sourceExtension = sourceExtension;
    }

    public String displayName() {
        return displayName;
    }

    /** Ekstensi berkas kode utama bahasa ini (tanpa titik). */
    public String sourceExtension() {
        return sourceExtension;
    }
}

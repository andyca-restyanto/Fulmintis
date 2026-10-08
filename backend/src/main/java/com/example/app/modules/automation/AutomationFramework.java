// filepath: /backend/src/main/java/com/example/app/modules/automation/AutomationFramework.java
package com.example.app.modules.automation;

public enum AutomationFramework {
    PLAYWRIGHT("Playwright"),
    CYPRESS("Cypress"),
    SELENIUM("Selenium");

    private final String displayName;

    AutomationFramework(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

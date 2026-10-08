// filepath: /backend/src/main/java/com/example/app/modules/automation/AutomationCompatibility.java
package com.example.app.modules.automation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matriks framework x bahasa yang valid -- SATU sumber kebenaran: backend memvalidasi
 * dengan ini dan menyajikannya ke frontend lewat endpoint opsi (frontend tidak menulis
 * ulang aturannya).
 * <pre>
 *            Java  JavaScript  TypeScript  Python
 * Playwright  v       v           v          v
 * Selenium    v       v           v (*)      v
 * Cypress     -       v           v          -
 * </pre>
 * (*) Selenium untuk TypeScript memakai binding JavaScript (selenium-webdriver) dgn type.
 */
public final class AutomationCompatibility {

    private static final Map<AutomationFramework, Set<AutomationLanguage>> SUPPORTED = new EnumMap<>(AutomationFramework.class);

    static {
        SUPPORTED.put(AutomationFramework.PLAYWRIGHT, EnumSet.allOf(AutomationLanguage.class));
        SUPPORTED.put(AutomationFramework.SELENIUM, EnumSet.allOf(AutomationLanguage.class));
        SUPPORTED.put(AutomationFramework.CYPRESS, EnumSet.of(AutomationLanguage.JAVASCRIPT, AutomationLanguage.TYPESCRIPT));
    }

    private AutomationCompatibility() {
    }

    public static boolean isSupported(AutomationFramework framework, AutomationLanguage language) {
        return SUPPORTED.getOrDefault(framework, Set.of()).contains(language);
    }

    /** Bahasa yang valid per framework, berurutan sesuai deklarasi enum (stabil utk UI). */
    public static Map<AutomationFramework, List<AutomationLanguage>> matrix() {
        Map<AutomationFramework, List<AutomationLanguage>> result = new LinkedHashMap<>();
        for (AutomationFramework framework : AutomationFramework.values()) {
            List<AutomationLanguage> languages = new ArrayList<>();
            for (AutomationLanguage language : AutomationLanguage.values()) {
                if (isSupported(framework, language)) {
                    languages.add(language);
                }
            }
            result.put(framework, languages);
        }
        return result;
    }

    public static String unsupportedMessage(AutomationFramework framework, AutomationLanguage language) {
        return framework.displayName() + " tidak mendukung " + language.displayName() + ".";
    }
}

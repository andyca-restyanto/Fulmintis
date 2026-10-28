// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/exception/DuplicateAllowlistRuleException.java
package com.example.app.modules.emailallowlist.exception;

public class DuplicateAllowlistRuleException extends RuntimeException {
    public DuplicateAllowlistRuleException(String value) {
        super("Aturan '" + value + "' sudah ada di daftar");
    }
}

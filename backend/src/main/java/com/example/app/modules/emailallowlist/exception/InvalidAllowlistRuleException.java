// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/exception/InvalidAllowlistRuleException.java
package com.example.app.modules.emailallowlist.exception;

public class InvalidAllowlistRuleException extends RuntimeException {
    public InvalidAllowlistRuleException(String message) {
        super(message);
    }
}

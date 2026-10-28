// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/exception/AllowlistRuleNotFoundException.java
package com.example.app.modules.emailallowlist.exception;

public class AllowlistRuleNotFoundException extends RuntimeException {
    public AllowlistRuleNotFoundException(Long id) {
        super("Aturan email dengan id " + id + " tidak ditemukan");
    }
}

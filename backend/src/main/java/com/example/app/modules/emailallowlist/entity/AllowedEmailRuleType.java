// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/entity/AllowedEmailRuleType.java
package com.example.app.modules.emailallowlist.entity;

/** EMAIL = alamat persis (budi@gmail.com); DOMAIN = semua alamat di domain itu persis (@perusahaan.com, subdomain TIDAK ikut). */
public enum AllowedEmailRuleType {
    EMAIL,
    DOMAIN
}

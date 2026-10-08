// filepath: /backend/src/main/java/com/example/app/modules/testcase/dto/TestCaseDraftStepDTO.java
package com.example.app.modules.testcase.dto;

/** Satu langkah draft beserta hasil yang diharapkan (berpasangan). Match dgn TestCaseDraftStep (FE). */
public record TestCaseDraftStepDTO(String action, String expected) {
}

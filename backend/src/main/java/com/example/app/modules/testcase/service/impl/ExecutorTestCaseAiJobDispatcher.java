// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/impl/ExecutorTestCaseAiJobDispatcher.java
package com.example.app.modules.testcase.service.impl;

import com.example.app.modules.testcase.service.TestCaseAiJobDispatcher;
import com.example.app.modules.testcase.service.TestCaseAiWorker;
import com.example.app.shared.ai.AiMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Memakai thread pool terbatas yang sama dgn Automation (satu kolam utk semua job AI: total beban terkendali). */
@Slf4j
@Component
public class ExecutorTestCaseAiJobDispatcher implements TestCaseAiJobDispatcher {

    private final ExecutorService automationExecutor;
    private final TestCaseAiWorker worker;

    public ExecutorTestCaseAiJobDispatcher(ExecutorService automationExecutor, TestCaseAiWorker worker) {
        this.automationExecutor = automationExecutor;
        this.worker = worker;
    }

    @Override
    public void dispatch(UUID generationId, String requirement) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(generationId, requirement);
                }
            });
        } else {
            submit(generationId, requirement);
        }
    }

    private void submit(UUID generationId, String requirement) {
        try {
            automationExecutor.execute(() -> {
                try {
                    worker.run(generationId, requirement);
                } catch (RuntimeException e) {
                    log.error("Worker generate test case {} berhenti tak terduga", generationId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Antrean job AI penuh; generate test case {} ditolak.", generationId);
            worker.failQueued(generationId, AiMessages.SERVER_BUSY, AiMessages.SERVER_BUSY_MESSAGE);
        }
    }
}

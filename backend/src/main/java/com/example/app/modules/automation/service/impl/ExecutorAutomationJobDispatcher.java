// filepath: /backend/src/main/java/com/example/app/modules/automation/service/impl/ExecutorAutomationJobDispatcher.java
package com.example.app.modules.automation.service.impl;

import com.example.app.modules.automation.AutomationMessages;
import com.example.app.modules.automation.service.AutomationGenerationWorker;
import com.example.app.modules.automation.service.AutomationJobDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class ExecutorAutomationJobDispatcher implements AutomationJobDispatcher {

    private final ExecutorService automationExecutor;
    private final AutomationGenerationWorker worker;

    public ExecutorAutomationJobDispatcher(ExecutorService automationExecutor, AutomationGenerationWorker worker) {
        this.automationExecutor = automationExecutor;
        this.worker = worker;
    }

    @Override
    public void dispatch(UUID generationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Tunggu commit: kalau worker jalan lebih dulu dari commit, baris job belum terlihat olehnya.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(generationId);
                }
            });
        } else {
            submit(generationId);
        }
    }

    private void submit(UUID generationId) {
        try {
            automationExecutor.execute(() -> {
                try {
                    worker.run(generationId);
                } catch (RuntimeException e) {
                    log.error("Worker automation {} berhenti tak terduga", generationId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Antrean generate automation penuh; job {} ditolak.", generationId);
            worker.failQueued(generationId, AutomationMessages.SERVER_BUSY, AutomationMessages.SERVER_BUSY_MESSAGE);
        }
    }
}

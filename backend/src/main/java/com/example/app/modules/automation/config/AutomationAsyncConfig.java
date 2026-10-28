// filepath: /backend/src/main/java/com/example/app/modules/automation/config/AutomationAsyncConfig.java
package com.example.app.modules.automation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread pool TERBATAS untuk job generate (antrean penuh -> job ditolak dgn SERVER_BUSY, bukan menumpuk
 * tanpa batas). Ada di memori satu instance: job yang sedang berjalan saat proses mati ditandai gagal
 * saat startup (AutomationStartupRecovery).
 */
@Configuration
public class AutomationAsyncConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService automationExecutor(
            @Value("${app.automation.executor.core-size:2}") int coreSize,
            @Value("${app.automation.executor.max-size:4}") int maxSize,
            @Value("${app.automation.executor.queue-capacity:20}") int queueCapacity
    ) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "automation-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(coreSize, Math.max(coreSize, maxSize), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }
}

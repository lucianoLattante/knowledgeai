package io.github.lucianolattante.knowledgeai.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.lucianolattante.knowledgeai.documents.DocumentService.Identity;

@Component
class RequestBudget {
    private static final Duration PERIOD = Duration.ofMinutes(1);
    private final Clock clock;
    private final int uploadLimit;
    private final int retrievalLimit;
    private final ConcurrentHashMap<BudgetKey, Window> budgets = new ConcurrentHashMap<>();

    RequestBudget(Clock clock, @Value("${knowledgeai.limits.uploads-per-minute}") int uploadLimit,
                  @Value("${knowledgeai.limits.retrievals-per-minute}") int retrievalLimit) {
        if (uploadLimit <= 0 || retrievalLimit <= 0) {
            throw new IllegalArgumentException("Request limits must be positive");
        }
        this.clock = clock;
        this.uploadLimit = uploadLimit;
        this.retrievalLimit = retrievalLimit;
    }

    void upload(Identity owner) {
        admit(new BudgetKey(owner, Workload.UPLOAD), uploadLimit);
    }

    void retrieve(Identity owner) {
        admit(new BudgetKey(owner, Workload.RETRIEVAL), retrievalLimit);
    }

    private void admit(BudgetKey key, int limit) {
        budgets.compute(key, (ignored, current) -> {
            Instant now = clock.instant();
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(now.plus(PERIOD), 1);
            }
            if (current.used() >= limit) {
                Duration remaining = Duration.between(now, current.expiresAt());
                long retryAfter = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
                throw new LimitExceeded(retryAfter);
            }
            return new Window(current.expiresAt(), current.used() + 1);
        });
    }

    private enum Workload { UPLOAD, RETRIEVAL }
    private record BudgetKey(Identity owner, Workload workload) {}
    private record Window(Instant expiresAt, int used) {}

    static class LimitExceeded extends RuntimeException {
        private final long retryAfter;

        LimitExceeded(long retryAfter) {
            this.retryAfter = retryAfter;
        }

        long retryAfter() {
            return retryAfter;
        }
    }
}

package com.paytm.wallet.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransferMetrics {
    private final Counter created;
    private final Counter declined;
    private final Counter replayed;

    public TransferMetrics(MeterRegistry registry) {
        created = Counter.builder("wallet.transfers.created").description("Transfers created").register(registry);
        declined = Counter.builder("wallet.transfers.declined.insufficient_funds").description("Declined transfers").register(registry);
        replayed = Counter.builder("wallet.transfers.idempotent_replays").description("Idempotent transfer replays").register(registry);
    }

    public Counter created() { return created; }
    public Counter declined() { return declined; }
    public Counter replayed() { return replayed; }
}

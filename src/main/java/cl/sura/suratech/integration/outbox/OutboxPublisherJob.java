package cl.sura.suratech.integration.outbox;

import cl.sura.suratech.entity.OutboxEventEntity;
import cl.sura.suratech.integration.servicebus.QuoteIssuedPublisher;
import cl.sura.suratech.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnBean(QuoteIssuedPublisher.class)
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

    private final OutboxEventRepository outboxRepository;
    private final QuoteIssuedPublisher publisher;

    private final int batchSize;
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final boolean enabled;

    public OutboxPublisherJob(
            OutboxEventRepository outboxRepository,
            QuoteIssuedPublisher publisher,
            @Value("${app.outbox.poll.batch-size:50}") int batchSize,
            @Value("${app.outbox.retry.max-attempts:10}") int maxAttempts,
            @Value("${app.outbox.retry.base-backoff-ms:500}") long baseBackoffMs,
            @Value("${app.outbox.enabled:true}") boolean enabled
    ) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll.fixed-delay:1000}")
    @Transactional
    public void tick() {
        if (!enabled) return;
        publishBatch();
    }

    protected void publishBatch() {
        OffsetDateTime now = OffsetDateTime.now();

        List<OutboxEventEntity> batch = outboxRepository.lockBatchReadyToProcess(
                OutboxEventEntity.Status.NEW,
                now,
                PageRequest.of(0, batchSize)
        );

        if (batch.isEmpty()) return;

        for (OutboxEventEntity e : batch) {
            e.setStatus(OutboxEventEntity.Status.PROCESSING);
        }
        outboxRepository.saveAll(batch);

        for (OutboxEventEntity e : batch) {
            try {
                publisher.publishCloudEventJson(
                        e.getEventId(),
                        e.getAggregateId(),
                        e.getPayloadJson().toString()
                );

                e.setStatus(OutboxEventEntity.Status.SENT);
                e.setLastError(null);
                log.info("outbox.published eventId={} type={} aggregateId={}", e.getEventId(), e.getEventType(), e.getAggregateId());
            } catch (Exception ex) {
                int attempts = e.getAttempts() + 1;
                e.setAttempts(attempts);

                if (attempts >= maxAttempts) {
                    e.setStatus(OutboxEventEntity.Status.FAILED);
                } else {
                    e.setStatus(OutboxEventEntity.Status.NEW);
                    e.setNextAttemptAt(OffsetDateTime.now().plusNanos(backoffNanos(attempts)));
                }

                e.setLastError(safeError(ex));
                log.warn("outbox.publish_failed eventId={} attempts={} status={}", e.getEventId(), e.getAttempts(), e.getStatus(), ex);
            }
        }

        outboxRepository.saveAll(batch);
    }

    private long backoffNanos(int attempts) {
        long factor = 1L << Math.min(attempts - 1, 6);
        long backoffMs = Math.min(baseBackoffMs * factor, 30_000L);
        return backoffMs * 1_000_000L;
    }

    private String safeError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) msg = ex.getClass().getSimpleName();
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }
}
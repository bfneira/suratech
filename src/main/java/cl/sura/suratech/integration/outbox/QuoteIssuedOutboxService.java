package cl.sura.suratech.integration.outbox;

import cl.sura.suratech.integration.events.CloudEvent;
import cl.sura.suratech.integration.events.QuoteIssuedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class QuoteIssuedOutboxService {

    public static final String EVENT_TYPE = "com.suratech.quote.issued.v1";
    public static final String EVENT_SOURCE = "/suratech/quotes";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public QuoteIssuedOutboxService(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueQuoteIssued(QuoteIssuedEvent data) {
        try {
            String eventId = UUID.randomUUID().toString();
            OffsetDateTime now = OffsetDateTime.now();

            CloudEvent<QuoteIssuedEvent> cloudEvent = CloudEvent.v1(
                    EVENT_TYPE,
                    EVENT_SOURCE,
                    eventId,
                    now,
                    "quotes/" + data.quoteId(),
                    "application/json",
                    null,
                    data
            );

            JsonNode payloadJson = objectMapper.valueToTree(cloudEvent);

            OutboxEvent e = new OutboxEvent();
            e.setEventId(eventId);
            e.setEventType(EVENT_TYPE);
            e.setAggregateType("Quote");
            e.setAggregateId(data.quoteId());
            e.setPayloadJson(payloadJson);
            e.setStatus(OutboxEvent.Status.NEW);
            e.setAttempts(0);
            e.setNextAttemptAt(now);
            e.setCreatedAt(now);

            outboxRepository.save(e);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enqueue QuoteIssued outbox event", ex);
        }
    }
}
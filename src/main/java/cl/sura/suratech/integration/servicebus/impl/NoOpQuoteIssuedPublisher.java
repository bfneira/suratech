package cl.sura.suratech.integration.servicebus.impl;

import cl.sura.suratech.integration.servicebus.QuoteIssuedPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.messaging.servicebus.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpQuoteIssuedPublisher implements QuoteIssuedPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpQuoteIssuedPublisher.class);

    @Override
    public void publishCloudEventJson(String eventId, String quoteId, String cloudEventJson) {
        log.info("🧪 NoOp publisher: simulated publish eventId={}, quoteId={}", eventId, quoteId);
    }
}
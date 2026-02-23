package cl.sura.suratech.integration.servicebus.impl;

import cl.sura.suratech.integration.servicebus.QuoteIssuedPublisher;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        name = "app.messaging.servicebus.enabled",
        havingValue = "true"
)
public class QuoteIssuedPublisherImpl implements QuoteIssuedPublisher {

    private final ServiceBusSenderClient sender;

    public QuoteIssuedPublisherImpl(ServiceBusSenderClient sender) {
        this.sender = sender;
    }

    @Override
    public void publishCloudEventJson(String eventId, String quoteId, String cloudEventJson) {
        ServiceBusMessage msg = new ServiceBusMessage(cloudEventJson.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/cloudevents+json");

        msg.setMessageId(eventId);

        msg.getApplicationProperties().put("ce_type", "com.suratech.quote.issued.v1");
        msg.getApplicationProperties().put("quote_id", quoteId);
        msg.getApplicationProperties().put("schema_version", 1);

        sender.sendMessage(msg);
    }
}
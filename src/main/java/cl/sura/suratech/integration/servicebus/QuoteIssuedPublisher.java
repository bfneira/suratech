package cl.sura.suratech.integration.servicebus;


public interface QuoteIssuedPublisher {
    void publishCloudEventJson(String eventId, String quoteId, String cloudEventJson);
}
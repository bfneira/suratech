package cl.sura.suratech.integration.servicebus;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.messaging.servicebus.enabled", havingValue = "true")
public class ServiceBusConfig {

    @Bean
    ServiceBusSenderClient quoteEventsSender(
            @Value("${app.messaging.servicebus.connection-string}") String connectionString,
            @Value("${app.messaging.servicebus.topic:quotes}") String topicName
    ) {
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException(
                    "Service Bus is enabled but app.messaging.servicebus.connection-string is empty. " +
                            "Set AZURE_SERVICEBUS_CONNECTION_STRING or disable app.messaging.servicebus.enabled."
            );
        }

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .topicName(topicName)
                .buildClient();
    }
}
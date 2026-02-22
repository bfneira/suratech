package cl.sura.suratech.integration.servicebus;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceBusConfig {

    @Bean
    ServiceBusSenderClient quoteEventsSender(
            @Value("${app.messaging.servicebus.connection-string}") String connectionString,
            @Value("${app.messaging.servicebus.topic}") String topicName
    ) {
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .topicName(topicName)
                .buildClient();
    }
}
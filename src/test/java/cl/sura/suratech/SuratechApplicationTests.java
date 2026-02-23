package cl.sura.suratech;

import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.outbox.enabled=false"
})
class SuratechApplicationTests {

    @MockBean
    ServiceBusSenderClient quoteEventsSender;

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.3")
                    .withDatabaseName("suratech")
                    .withUsername("root")
                    .withPassword("root");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @Test
    void contextLoads() {
    }
}
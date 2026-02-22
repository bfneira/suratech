package cl.sura.suratech.integration.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_event",
        indexes = @Index(name = "idx_outbox_status_next", columnList = "status,nextAttemptAt"),
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_event_id", columnNames = "eventId"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false, length = 200)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Lob
    @Column(nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Lob
    private String lastError;

    public enum Status {
        NEW, PROCESSING, SENT, FAILED
    }
}
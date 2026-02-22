package cl.sura.suratech.integration.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select e
           from OutboxEvent e
           where e.status = :status
             and e.nextAttemptAt <= :now
           order by e.createdAt asc
           """)
    List<OutboxEvent> lockBatchReadyToProcess(
            @Param("status") OutboxEvent.Status status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );
}
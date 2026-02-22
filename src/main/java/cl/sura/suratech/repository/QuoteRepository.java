package cl.sura.suratech.repository;

import cl.sura.suratech.entity.QuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> { }

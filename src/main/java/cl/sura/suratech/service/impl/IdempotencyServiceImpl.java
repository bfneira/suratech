package cl.sura.suratech.service.impl;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.entity.IdempotencyKeyEntity;
import cl.sura.suratech.exception.IdempotencyConflictException;
import cl.sura.suratech.repository.IdempotencyKeyRepository;
import cl.sura.suratech.service.IdempotencyService;
import cl.sura.suratech.service.QuoteLookupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {
    private final IdempotencyKeyRepository repo;
    private final QuoteLookupService quoteLookupService;
    private final long ttlSeconds;

    public IdempotencyServiceImpl(IdempotencyKeyRepository repo,
                                     QuoteLookupService quoteLookupService,
                                     @Value("${idempotency.ttlSeconds}") long ttlSeconds) {
        this.repo = repo;
        this.quoteLookupService = quoteLookupService;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    @Transactional
    public IdempotencyResult getOrCompute(UUID key, QuoteCreateRequest request, Supplier<QuoteResponse> supplier) {
        String hash = sha256(request.toString());

        Optional<IdempotencyKeyEntity> existingOpt = repo.findById(key);
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            if (existing.getExpiresAt().isAfter(OffsetDateTime.now())) {
                if (!existing.getRequestHash().equals(hash)) {
                    throw new IdempotencyConflictException(key.toString());
                }
                QuoteResponse replay = quoteLookupService.getQuoteResponse(existing.getQuoteId());
                return new IdempotencyResult(replay, true);
            }
        }

        QuoteResponse created = supplier.get();

        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.setIdempotencyKey(key);
        entity.setRequestHash(hash);
        entity.setQuoteId(UUID.fromString(created.id()));
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setExpiresAt(OffsetDateTime.now().plusSeconds(ttlSeconds));
        repo.save(entity);

        return new IdempotencyResult(created, false);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Hash error", e);
        }
    }
}

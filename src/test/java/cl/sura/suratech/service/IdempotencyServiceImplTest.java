package cl.sura.suratech.service;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.entity.IdempotencyKeyEntity;
import cl.sura.suratech.exception.IdempotencyConflictException;
import cl.sura.suratech.repository.IdempotencyKeyRepository;
import cl.sura.suratech.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static cl.sura.suratech.testsupport.QuoteTestData.quoteResponse;
import static cl.sura.suratech.testsupport.QuoteTestData.randomIdempotencyKeyV4;
import static cl.sura.suratech.testsupport.QuoteTestData.validCreateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    @Mock IdempotencyKeyRepository repo;
    @Mock QuoteLookupService quoteLookupService;

    @Captor ArgumentCaptor<IdempotencyKeyEntity> entityCaptor;

    @Test
    void getOrCompute_whenNewKey_thenComputesAndPersists_andReturnsNotReplayed() {
        // Arrange
        UUID key = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        long ttlSeconds = 86_400L;
        IdempotencyServiceImpl service = new IdempotencyServiceImpl(repo, quoteLookupService, ttlSeconds);

        OffsetDateTime createdAt = OffsetDateTime.parse("2026-02-23T12:00:00Z");
        QuoteResponse created = quoteResponse("b0db4b2c-9de2-4c23-8f22-4adf4d62c3fa", createdAt);

        when(repo.findById(key)).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Supplier<QuoteResponse> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn(created);

        // Act
        IdempotencyService.IdempotencyResult result = service.getOrCompute(key, request, supplier);

        // Assert
        assertThat(result.replayed()).isFalse();
        assertThat(result.quote()).isEqualTo(created);

        verify(supplier, times(1)).get();
        verify(quoteLookupService, never()).getQuoteResponse(any());

        verify(repo, times(1)).save(entityCaptor.capture());
        IdempotencyKeyEntity saved = entityCaptor.getValue();

        assertThat(saved.getIdempotencyKey()).isEqualTo(key);
        assertThat(saved.getRequestHash()).isNotBlank();
        assertThat(saved.getRequestHash()).hasSize(64);
        assertThat(saved.getQuoteId()).isEqualTo(UUID.fromString(created.id()));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
    }

    @Test
    void getOrCompute_whenExistingNotExpiredAndSameHash_thenReplays_andDoesNotInsert() {
        UUID key = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        long ttlSeconds = 86_400L;
        IdempotencyServiceImpl service = new IdempotencyServiceImpl(repo, quoteLookupService, ttlSeconds);

        QuoteResponse created = quoteResponse(
                "b0db4b2c-9de2-4c23-8f22-4adf4d62c3fa",
                OffsetDateTime.parse("2026-02-23T12:00:00Z")
        );

        String hash = sha256Hex(request.toString());

        UUID quoteId = UUID.fromString(created.id());
        IdempotencyKeyEntity existing = new IdempotencyKeyEntity();
        existing.setIdempotencyKey(key);
        existing.setRequestHash(hash);
        existing.setQuoteId(quoteId);
        existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        existing.setExpiresAt(OffsetDateTime.now().plusMinutes(10));

        when(repo.findById(key)).thenReturn(Optional.of(existing));
        when(quoteLookupService.getQuoteResponse(quoteId)).thenReturn(created);

        @SuppressWarnings("unchecked")
        Supplier<QuoteResponse> supplier = mock(Supplier.class);

        IdempotencyService.IdempotencyResult result = service.getOrCompute(key, request, supplier);

        assertThat(result.replayed()).isTrue();
        assertThat(result.quote()).isEqualTo(created);

        verify(supplier, never()).get();
        verify(repo, never()).save(any());
        verify(quoteLookupService, times(1)).getQuoteResponse(quoteId);
    }

    @Test
    void getOrCompute_whenExistingNotExpiredAndDifferentHash_thenThrows409_andDoesNotInsertOrCompute() {
        // Arrange
        UUID key = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        long ttlSeconds = 86_400L;
        IdempotencyServiceImpl service = new IdempotencyServiceImpl(repo, quoteLookupService, ttlSeconds);

        IdempotencyKeyEntity existing = new IdempotencyKeyEntity();
        existing.setIdempotencyKey(key);
        existing.setRequestHash("0".repeat(64)); // intentionally wrong
        existing.setQuoteId(UUID.fromString("b0db4b2c-9de2-4c23-8f22-4adf4d62c3fa"));
        existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        existing.setExpiresAt(OffsetDateTime.now().plusMinutes(10));

        when(repo.findById(key)).thenReturn(Optional.of(existing));

        @SuppressWarnings("unchecked")
        Supplier<QuoteResponse> supplier = mock(Supplier.class);

        // Act + Assert
        assertThatThrownBy(() -> service.getOrCompute(key, request, supplier))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(supplier, never()).get();
        verify(repo, never()).save(any());
        verifyNoInteractions(quoteLookupService);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
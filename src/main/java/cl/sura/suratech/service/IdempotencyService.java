package cl.sura.suratech.service;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;

import java.util.UUID;
import java.util.function.Supplier;

public interface IdempotencyService {
    IdempotencyResult getOrCompute(UUID key, QuoteCreateRequest request, Supplier<QuoteResponse> supplier);

    record IdempotencyResult(QuoteResponse quote, boolean replayed) {}
}

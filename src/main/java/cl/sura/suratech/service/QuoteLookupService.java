package cl.sura.suratech.service;

import cl.sura.suratech.dto.QuoteResponse;
import java.util.UUID;

public interface QuoteLookupService {
    QuoteResponse getQuoteResponse(UUID quoteId);
}
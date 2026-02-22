package cl.sura.suratech.service;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;

public interface QuoteApplicationService {
    QuoteResponse createQuote(QuoteCreateRequest request);
}

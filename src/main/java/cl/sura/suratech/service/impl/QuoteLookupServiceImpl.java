package cl.sura.suratech.service.impl;

import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.repository.QuoteRepository;
import cl.sura.suratech.service.QuoteLookupService;
import cl.sura.suratech.mapper.QuoteMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QuoteLookupServiceImpl implements QuoteLookupService {

    private final QuoteRepository quoteRepository;
    private final QuoteMapper mapper;

    public QuoteLookupServiceImpl(QuoteRepository quoteRepository,
                                  QuoteMapper mapper) {
        this.quoteRepository = quoteRepository;
        this.mapper = mapper;
    }

    @Override
    public QuoteResponse getQuoteResponse(UUID quoteId) {
        var quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalStateException("Quote not found: " + quoteId));

        return mapper.toResponse(quote);
    }
}
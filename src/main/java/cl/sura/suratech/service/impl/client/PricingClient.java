package cl.sura.suratech.service.impl.client;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.service.AggregationService;

import java.util.List;

public interface PricingClient {
    PricingResult price(QuoteCreateRequest request);
    record PricingResult(List<AggregationService.ItemComputed> items) {}
}

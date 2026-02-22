package cl.sura.suratech.service;

import cl.sura.suratech.dto.QuoteCreateRequest;

import java.util.List;

public interface AggregationService {
    AggregationResult aggregate(QuoteCreateRequest request);

    record AggregationResult(List<ItemComputed> items, double subtotal, double taxTotal, double grandTotal) {}

    record ItemComputed(
            String sku,
            String name,
            int quantity,
            double unitPrice,
            double taxRate,
            double lineTotal,
            double taxAmount
    ) {}
}


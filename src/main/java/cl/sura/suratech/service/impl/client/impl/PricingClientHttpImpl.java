package cl.sura.suratech.service.impl.client.impl;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.service.AggregationService;
import cl.sura.suratech.service.impl.client.PricingClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

@Component
public class PricingClientHttpImpl implements PricingClient {

    @Override
    @CircuitBreaker(name = "pricing")
    @Retry(name = "pricing")
    @Bulkhead(name = "pricing", type = Bulkhead.Type.SEMAPHORE)
    public PricingResult price(QuoteCreateRequest request) {
        var items = request.items().stream().map(it -> {
            double lineTotal = it.quantity() * it.unitPrice();
            double taxRate = it.taxRate() == null ? 0 : it.taxRate();
            return new AggregationService.ItemComputed(
                    it.sku(), it.name(), it.quantity(), it.unitPrice(), taxRate,
                    lineTotal, lineTotal * taxRate
            );
        }).toList();

        return new PricingResult(items);
    }
}

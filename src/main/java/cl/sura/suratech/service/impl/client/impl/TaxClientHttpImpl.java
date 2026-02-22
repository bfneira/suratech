package cl.sura.suratech.service.impl.client.impl;

import cl.sura.suratech.service.AggregationService;
import cl.sura.suratech.service.impl.client.PricingClient;
import cl.sura.suratech.service.impl.client.TaxClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

@Component
public class TaxClientHttpImpl implements TaxClient {

    @Override
    @CircuitBreaker(name = "tax")
    @Retry(name = "tax")
    @Bulkhead(name = "tax", type = Bulkhead.Type.SEMAPHORE)
    public TaxResult calculateTaxes(PricingClient.PricingResult priced) {
        double total = priced.items().stream().mapToDouble(AggregationService.ItemComputed::taxAmount).sum();
        return new TaxResult(total);
    }
}

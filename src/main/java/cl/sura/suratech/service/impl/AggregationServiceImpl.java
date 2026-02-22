package cl.sura.suratech.service.impl;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.service.AggregationService;
import cl.sura.suratech.service.impl.client.PricingClient;
import cl.sura.suratech.service.impl.client.TaxClient;
import org.springframework.stereotype.Service;

@Service
public class AggregationServiceImpl implements AggregationService {
    private final PricingClient pricingClient;
    private final TaxClient taxClient;

    public AggregationServiceImpl(PricingClient pricingClient, TaxClient taxClient) {
        this.pricingClient = pricingClient;
        this.taxClient = taxClient;
    }

    @Override
    public AggregationResult aggregate(QuoteCreateRequest request) {
        var priced = pricingClient.price(request);
        var tax = taxClient.calculateTaxes(priced);

        double subtotal = priced.items().stream().mapToDouble(ItemComputed::lineTotal).sum();
        double taxTotal = tax.taxTotal();
        double grandTotal = subtotal + taxTotal;

        return new AggregationResult(priced.items(), subtotal, taxTotal, grandTotal);
    }
}

package cl.sura.suratech.service.impl.client;

public interface TaxClient {
    TaxResult calculateTaxes(PricingClient.PricingResult priced);
    record TaxResult(double taxTotal) {}
}

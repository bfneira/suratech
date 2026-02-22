package cl.sura.suratech.integration.events;

import java.time.OffsetDateTime;
import java.util.List;

public record QuoteIssuedEvent(
        String quoteId,
        OffsetDateTime issuedAt,
        Customer customer,
        String currency,
        Totals totals,
        List<Item> items,
        String idempotencyKey,
        int version
) {
    public record Customer(String customerId) {}

    public record Totals(double subtotal, double taxTotal, double grandTotal) {}

    public record Item(
            String sku,
            String name,
            int quantity,
            double unitPrice,
            double taxRate,
            double lineTotal,
            double taxAmount
    ) {}
}
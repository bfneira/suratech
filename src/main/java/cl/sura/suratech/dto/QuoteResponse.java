package cl.sura.suratech.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record QuoteResponse(
        String id,
        String documentId,
        String status,
        String currency,
        Customer customer,
        List<Item> items,
        Totals totals,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        Map<String, String> metadata
) {
    public record Customer(String id, String email) {}
    public record Item(String sku, String name, int quantity, double unitPrice, double taxRate, double lineTotal, double taxAmount) {}
    public record Totals(double subtotal, double taxTotal, double grandTotal) {}
}

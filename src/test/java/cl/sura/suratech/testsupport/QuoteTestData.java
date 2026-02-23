package cl.sura.suratech.testsupport;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuoteTestData {

    private QuoteTestData() {}

    public static QuoteCreateRequest validCreateRequest() {
        return new QuoteCreateRequest(
                "DOC-2026-000001",
                new QuoteCreateRequest.Customer("CUST-12345", "customer@example.com"),
                "CLP",
                List.of(new QuoteCreateRequest.Item("SKU-001", "Item 1", 2, 10_000.0, 0.19)),
                OffsetDateTime.parse("2026-03-01T12:00:00Z"),
                Map.of("channel", "test")
        );
    }

    public static QuoteCreateRequest invalidCreateRequest_missingRequiredFields() {
        return new QuoteCreateRequest(
                "",
                null,
                "clp",
                List.of(),
                null,
                Map.of()
        );
    }

    public static QuoteResponse quoteResponse(String id, OffsetDateTime createdAt) {
        return new QuoteResponse(
                id,
                "DOC-2026-000001",
                "ISSUED",
                "CLP",
                new QuoteResponse.Customer("CUST-12345", "customer@example.com"),
                List.of(new QuoteResponse.Item("SKU-001", "Item 1", 2, 10_000.0, 0.19, 20_000.0, 3_800.0)),
                new QuoteResponse.Totals(20_000.0, 3_800.0, 23_800.0),
                OffsetDateTime.parse("2026-03-01T12:00:00Z"),
                createdAt,
                Map.of("channel", "test")
        );
    }

    public static UUID randomIdempotencyKeyV4() {
        return UUID.fromString("2f4c4d7a-9b3f-4b2a-9f2b-8a2d2e4e1a11");
    }
}
package cl.sura.suratech.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record QuoteCreateRequest(
        @NotBlank @Size(max = 64) String documentId,
        @NotNull @Valid Customer customer,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotEmpty @Size(max = 200) @Valid List<Item> items,
        OffsetDateTime expiresAt,
        @Size(max = 50) Map<String, @Size(max = 200) String> metadata
) {
    public record Customer(
            @NotBlank @Size(max = 64) String id,
            @Email @Size(max = 254) String email
    ) {}

    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Min(1) @Max(100000) Integer quantity,
            @NotNull @DecimalMin("0.0") @DecimalMax("999999999") Double unitPrice,
            @DecimalMin("0.0") @DecimalMax("1.0") Double taxRate
    ) {}
}

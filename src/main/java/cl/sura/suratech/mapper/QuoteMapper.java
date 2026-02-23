package cl.sura.suratech.mapper;

import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.entity.QuoteEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class QuoteMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuoteResponse toResponse(QuoteEntity quoteEntity) {
        return new QuoteResponse(
                quoteEntity.getId().toString(),
                quoteEntity.getDocumentId(),
                quoteEntity.getStatus(),
                quoteEntity.getCurrency(),
                new QuoteResponse.Customer(quoteEntity.getCustomerId(), quoteEntity.getCustomerEmail()),
                quoteEntity.getItems().stream().map(i -> new QuoteResponse.Item(
                        i.getSku(), i.getName(), i.getQuantity(),
                        i.getUnitPrice(), i.getTaxRate(),
                        i.getLineTotal(), i.getTaxAmount()
                )).toList(),
                new QuoteResponse.Totals(quoteEntity.getSubtotal(), quoteEntity.getTaxTotal(), quoteEntity.getGrandTotal()),
                quoteEntity.getExpiresAt(),
                quoteEntity.getCreatedAt(),
                fromJson(quoteEntity.getPayloadJson())
        );
    }

    public String toJson(Map<String, String> metadata) {
        try {
            if (metadata == null) return "{}";
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("metadata json error", e);
        }
    }

    private Map<String, String> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return Collections.emptyMap();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
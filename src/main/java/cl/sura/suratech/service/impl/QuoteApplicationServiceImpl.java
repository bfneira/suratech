package cl.sura.suratech.service.impl;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.entity.QuoteEntity;
import cl.sura.suratech.entity.QuoteItemEntity;
import cl.sura.suratech.mapper.QuoteMapper;
import cl.sura.suratech.repository.QuoteRepository;
import cl.sura.suratech.service.AggregationService;
import cl.sura.suratech.service.QuoteApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class QuoteApplicationServiceImpl implements QuoteApplicationService {

    private final AggregationService aggregationService;
    private final QuoteRepository quoteRepository;
    private final QuoteMapper mapper;

    public QuoteApplicationServiceImpl(AggregationService aggregationService,
                                          QuoteRepository quoteRepository,
                                          QuoteMapper mapper) {
        this.aggregationService = aggregationService;
        this.quoteRepository = quoteRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public QuoteResponse createQuote(QuoteCreateRequest request) {
        var agg = aggregationService.aggregate(request);

        UUID quoteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        QuoteEntity entity = new QuoteEntity();
        entity.setId(quoteId);
        entity.setDocumentId(request.documentId());
        entity.setStatus("ISSUED");
        entity.setCurrency(request.currency());
        entity.setCustomerId(request.customer().id());
        entity.setCustomerEmail(request.customer().email());
        entity.setSubtotal(agg.subtotal());
        entity.setTaxTotal(agg.taxTotal());
        entity.setGrandTotal(agg.grandTotal());
        entity.setExpiresAt(request.expiresAt());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setMetadataJson(mapper.toJson(request.metadata()));

        for (var it : agg.items()) {
            QuoteItemEntity item = new QuoteItemEntity();
            item.setQuote(entity);
            item.setSku(it.sku());
            item.setName(it.name());
            item.setQuantity(it.quantity());
            item.setUnitPrice(it.unitPrice());
            item.setTaxRate(it.taxRate());
            item.setLineTotal(it.lineTotal());
            item.setTaxAmount(it.taxAmount());
            entity.getItems().add(item);
        }

        return mapper.toResponse(quoteRepository.save(entity));
    }
}

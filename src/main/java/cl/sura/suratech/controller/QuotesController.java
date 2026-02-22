package cl.sura.suratech.controller;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.service.IdempotencyService;
import cl.sura.suratech.service.QuoteApplicationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static cl.sura.suratech.common.Constantes.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuotesController {

    private final QuoteApplicationService quoteService;
    private final IdempotencyService idempotencyService;

    public QuotesController(QuoteApplicationService quoteService,
                            IdempotencyService idempotencyService) {
        this.quoteService = quoteService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QuoteResponse> createQuote(
            @RequestHeader(HDR_IDEMPOTENCY_KEY) UUID idempotencyKey,
            @RequestHeader(value = HDR_REQUEST_ID, required = false) String requestId,
            @Valid @RequestBody QuoteCreateRequest quoteCreateRequest
    ) {
        boolean hasRequestId = requestId != null && !requestId.isBlank();

        if (hasRequestId) MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_IDEMPOTENCY_KEY, idempotencyKey.toString());

        try {
            var resultService = idempotencyService.getOrCompute(
                    idempotencyKey,
                    quoteCreateRequest,
                    () -> quoteService.createQuote(quoteCreateRequest)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.add(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString());
            headers.add(HDR_IDEMPOTENCY_STATUS, resultService.replayed() ? "replayed" : "created");
            if (hasRequestId) headers.add(HDR_REQUEST_ID, requestId);

            if (resultService.replayed()) {
                return ResponseEntity.ok().headers(headers).body(resultService.quote());
            }

            headers.setLocation(URI.create("/api/v1/quotes/" + resultService.quote().id()));
            return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(resultService.quote());
        } finally {
            if (hasRequestId) MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_IDEMPOTENCY_KEY);
        }
    }
}
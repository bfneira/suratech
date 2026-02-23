package cl.sura.suratech.controller;

import cl.sura.suratech.dto.QuoteCreateRequest;
import cl.sura.suratech.dto.QuoteResponse;
import cl.sura.suratech.exception.ApiExceptionHandler;
import cl.sura.suratech.exception.IdempotencyConflictException;
import cl.sura.suratech.service.IdempotencyService;
import cl.sura.suratech.service.QuoteApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static cl.sura.suratech.common.Constants.HDR_IDEMPOTENCY_KEY;
import static cl.sura.suratech.common.Constants.HDR_IDEMPOTENCY_STATUS;
import static cl.sura.suratech.common.Constants.HDR_REQUEST_ID;
import static cl.sura.suratech.testsupport.QuoteTestData.invalidCreateRequest_missingRequiredFields;
import static cl.sura.suratech.testsupport.QuoteTestData.quoteResponse;
import static cl.sura.suratech.testsupport.QuoteTestData.randomIdempotencyKeyV4;
import static cl.sura.suratech.testsupport.QuoteTestData.validCreateRequest;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QuotesController.class)
@Import(ApiExceptionHandler.class)
class QuotesControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean QuoteApplicationService quoteApplicationService;
    @MockBean IdempotencyService idempotencyService;

    @Test
    void createQuote_whenValidRequest_thenReturns201_andCallsService() throws Exception {
        // Arrange
        UUID idempotencyKey = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        OffsetDateTime createdAt = OffsetDateTime.parse("2026-02-23T12:00Z");
        QuoteResponse created = quoteResponse("b0db4b2c-9de2-4c23-8f22-4adf4d62c3fa", createdAt);

        when(quoteApplicationService.createQuote(any(QuoteCreateRequest.class))).thenReturn(created);

        when(idempotencyService.getOrCompute(eq(idempotencyKey), eq(request), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<QuoteResponse> supplier = inv.getArgument(2, Supplier.class);
                    QuoteResponse result = supplier.get();
                    return new IdempotencyService.IdempotencyResult(result, false);
                });

        // Act + Assert
        mockMvc.perform(
                        post("/api/v1/quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString())
                                .header(HDR_REQUEST_ID, "req-123")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString()))
                .andExpect(header().string(HDR_IDEMPOTENCY_STATUS, "created"))
                .andExpect(header().string("Location", "/api/v1/quotes/" + created.id()))
                .andExpect(jsonPath("$.id", is(created.id())))
                .andExpect(jsonPath("$.createdAt", is(createdAt.toInstant().toString())));

        verify(quoteApplicationService, times(1)).createQuote(eq(request));
    }

    @Test
    void createQuote_whenReplay_thenReturns200_andDoesNotCallService() throws Exception {
        // Arrange
        UUID idempotencyKey = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        OffsetDateTime createdAt = OffsetDateTime.parse("2026-02-23T12:00Z");
        QuoteResponse replay = quoteResponse("b0db4b2c-9de2-4c23-8f22-4adf4d62c3fa", createdAt);

        when(idempotencyService.getOrCompute(eq(idempotencyKey), eq(request), any()))
                .thenReturn(new IdempotencyService.IdempotencyResult(replay, true));

        // Act + Assert
        mockMvc.perform(
                        post("/api/v1/quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString())
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(header().string(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString()))
                .andExpect(header().string(HDR_IDEMPOTENCY_STATUS, "replayed"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id", is(replay.id())))
                .andExpect(jsonPath("$.createdAt", is(createdAt.toInstant().toString())));

        verifyNoInteractions(quoteApplicationService);

        ArgumentCaptor<Supplier<QuoteResponse>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(idempotencyService, times(1)).getOrCompute(eq(idempotencyKey), eq(request), supplierCaptor.capture());
        verifyNoMoreInteractions(idempotencyService);
    }

    @Test
    void createQuote_whenSameKeyDifferentBody_thenReturns409_andDoesNotCallService() throws Exception {
        // Arrange
        UUID idempotencyKey = randomIdempotencyKeyV4();
        QuoteCreateRequest request = validCreateRequest();

        when(idempotencyService.getOrCompute(eq(idempotencyKey), eq(request), any()))
                .thenThrow(new IdempotencyConflictException(idempotencyKey.toString()));

        // Act + Assert
        mockMvc.perform(
                        post("/api/v1/quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString())
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key reuse")));

        verifyNoInteractions(quoteApplicationService);
    }

    @Test
    void createQuote_whenMissingIdempotencyKeyHeader_thenReturns400() throws Exception {
        // Arrange
        QuoteCreateRequest request = validCreateRequest();

        // Act + Assert
        mockMvc.perform(
                        post("/api/v1/quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(idempotencyService);
        verifyNoInteractions(quoteApplicationService);
    }

    @Test
    void createQuote_whenInvalidPayload_thenReturns422_andDoesNotCallServices() throws Exception {
        // Arrange
        UUID idempotencyKey = randomIdempotencyKeyV4();
        QuoteCreateRequest invalid = invalidCreateRequest_missingRequiredFields();

        // Act + Assert
        mockMvc.perform(
                        post("/api/v1/quotes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .header(HDR_IDEMPOTENCY_KEY, idempotencyKey.toString())
                                .content(objectMapper.writeValueAsString(invalid))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        verifyNoInteractions(idempotencyService);
        verifyNoInteractions(quoteApplicationService);
    }
}
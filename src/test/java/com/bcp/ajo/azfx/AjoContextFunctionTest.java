package com.bcp.ajo.azfx;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import com.bcp.ajo.azfx.service.CosmosService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AjoContextFunctionTest {

    private CosmosService cosmosService;
    private AjoContextFunction function;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        cosmosService = mock(CosmosService.class);
        function = new AjoContextFunction(cosmosService);

        context = mock(ExecutionContext.class);
        when(context.getInvocationId()).thenReturn("inv-test-123");
        when(context.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }

    private record MockHttp(
        HttpRequestMessage<Optional<String>> request,
        HttpResponseMessage.Builder builder,
        HttpResponseMessage response
    ) {}

    @SuppressWarnings("unchecked")
    private MockHttp createMockHttp(String body, HttpStatus expectedStatus) {
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);

        when(request.getBody()).thenReturn(Optional.ofNullable(body));
        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(builder);
        when(builder.header(anyString(), anyString())).thenReturn(builder);
        when(builder.body(any())).thenReturn(builder);
        when(builder.build()).thenReturn(response);
        when(response.getStatusCode()).thenReturn(expectedStatus.value());
        when(response.getStatus()).thenReturn(expectedStatus);

        return new MockHttp(request, builder, response);
    }

    @Test
    @DisplayName("Retorna 400 Bad Request si el body está vacío")
    void shouldReturnBadRequestWhenBodyIsEmpty() {
        MockHttp mockHttp = createMockHttp("", HttpStatus.BAD_REQUEST);

        HttpResponseMessage res = function.run(mockHttp.request(), context);

        verify(mockHttp.request()).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(cosmosService);
        assertEquals(HttpStatus.BAD_REQUEST.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("Retorna 400 Bad Request si falta el campo wamid")
    void shouldReturnBadRequestWhenWamidIsMissing() {
        String json = """
            {
              "customerId": "BCP_12345",
              "journeyId": "campania-tc-v1"
            }
            """;
        MockHttp mockHttp = createMockHttp(json, HttpStatus.BAD_REQUEST);

        HttpResponseMessage res = function.run(mockHttp.request(), context);

        verify(mockHttp.request()).createResponseBuilder(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(cosmosService);
        assertEquals(HttpStatus.BAD_REQUEST.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("Procesa y almacena exitosamente un payload válido de CA2 y retorna 200 OK")
    void shouldStoreCorrelationAndReturnOkForValidPayload() {
        String json = """
            {
              "wamid": "wamid.HBgLMjAyNjEwMDU=",
              "customerId": "BCP_CLI_8877",
              "phone": "51999888777",
              "journeyId": "journey-creditos-2026",
              "journeyVersionId": "v2",
              "journeyVersionName": "Campaña Préstamo al Toque",
              "journeyNodeId": "node_send_wa",
              "journeyActionId": "action_ca1",
              "templateName": "bcp_prestamo_v1",
              "executionType": "CUSTOM"
            }
            """;
        MockHttp mockHttp = createMockHttp(json, HttpStatus.OK);

        HttpResponseMessage res = function.run(mockHttp.request(), context);

        verify(mockHttp.request()).createResponseBuilder(HttpStatus.OK);

        // Validar que se invocó a Cosmos con el documento mapeado correctamente
        ArgumentCaptor<CorrelationDocument> captor = ArgumentCaptor.forClass(CorrelationDocument.class);
        verify(cosmosService, times(1)).upsertCorrelation(captor.capture());

        CorrelationDocument captured = captor.getValue();
        assertEquals("wamid.HBgLMjAyNjEwMDU=", captured.getWamid());
        assertEquals("BCP_CLI_8877", captured.getCustomerId());
        assertEquals("journey-creditos-2026", captured.getJourneyId());
        assertEquals("CUSTOM", captured.getExecutionType());
        assertEquals("STORED", captured.getCorrelationStatus());
        assertNotNull(captured.getCorrelationId());
        assertEquals(604800, captured.getTtl());

        assertEquals(HttpStatus.OK.value(), res.getStatusCode());
    }
}

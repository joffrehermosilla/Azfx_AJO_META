package com.bcp.ajo.azfx;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import com.bcp.ajo.azfx.service.AjoWebhookService;
import com.bcp.ajo.azfx.service.CdpService;
import com.bcp.ajo.azfx.service.CosmosService;
import com.bcp.ajo.azfx.service.XdmMapper;
import com.bcp.ajo.azfx.util.MetaSignatureValidator;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MetaWebhookFunctionTest {

    private CosmosService cosmosService;
    private CdpService cdpService;
    private AjoWebhookService ajoService;
    private XdmMapper xdmMapper;
    private MetaSignatureValidator sigValidator;
    private MetaWebhookFunction function;
    private ExecutionContext context;

    private static final String VERIFY_TOKEN = "bcp_webhook_token_test";

    @BeforeEach
    void setUp() {
        cosmosService = mock(CosmosService.class);
        cdpService = mock(CdpService.class);
        ajoService = mock(AjoWebhookService.class);
        xdmMapper = spy(new XdmMapper());
        sigValidator = mock(MetaSignatureValidator.class);

        function = new MetaWebhookFunction(
            cosmosService,
            cdpService,
            ajoService,
            xdmMapper,
            sigValidator,
            VERIFY_TOKEN
        );

        context = mock(ExecutionContext.class);
        when(context.getInvocationId()).thenReturn("inv-meta-test");
        when(context.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }

    private record MockHttp(
        HttpRequestMessage<Optional<String>> request,
        HttpResponseMessage.Builder builder,
        HttpResponseMessage response
    ) {}

    @SuppressWarnings("unchecked")
    private MockHttp createMockHttp(
        String body,
        Map<String, String> headers,
        Map<String, String> queryParams,
        HttpStatus expectedStatus
    ) {
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);
        HttpResponseMessage response = mock(HttpResponseMessage.class);

        when(request.getBody()).thenReturn(Optional.ofNullable(body));
        when(request.getHeaders()).thenReturn(headers != null ? headers : Map.of());
        when(request.getQueryParameters()).thenReturn(queryParams != null ? queryParams : Map.of());

        when(request.createResponseBuilder(any(HttpStatus.class))).thenReturn(builder);
        when(builder.header(anyString(), anyString())).thenReturn(builder);
        when(builder.body(any())).thenReturn(builder);
        when(builder.build()).thenReturn(response);
        when(response.getStatusCode()).thenReturn(expectedStatus.value());
        when(response.getStatus()).thenReturn(expectedStatus);

        return new MockHttp(request, builder, response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests GET /meta-webhook (Challenge Verification)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET verify retorna 200 y el challenge cuando el verify_token coincide")
    void shouldReturn200AndChallengeWhenTokenMatches() {
        Map<String, String> params = Map.of(
            "hub.mode", "subscribe",
            "hub.verify_token", VERIFY_TOKEN,
            "hub.challenge", "challenge_code_9988"
        );
        MockHttp mockHttp = createMockHttp(null, null, params, HttpStatus.OK);

        HttpResponseMessage res = function.verify(mockHttp.request(), context);

        verify(mockHttp.builder()).body("challenge_code_9988");
        assertEquals(HttpStatus.OK.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("GET verify retorna 403 Forbidden cuando el verify_token es incorrecto")
    void shouldReturnForbiddenWhenTokenDoesNotMatch() {
        Map<String, String> params = Map.of(
            "hub.mode", "subscribe",
            "hub.verify_token", "wrong_token",
            "hub.challenge", "challenge_code_9988"
        );
        MockHttp mockHttp = createMockHttp(null, null, params, HttpStatus.FORBIDDEN);

        HttpResponseMessage res = function.verify(mockHttp.request(), context);

        verify(mockHttp.request()).createResponseBuilder(HttpStatus.FORBIDDEN);
        assertEquals(HttpStatus.FORBIDDEN.value(), res.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests POST /meta-webhook (Callbacks: Delivery & Tracking)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST callback rechaza con 403 si la firma HMAC-SHA256 no es válida")
    void shouldRejectWhenSignatureIsInvalid() {
        String body = "{\"object\":\"whatsapp_business_account\"}";
        Map<String, String> headers = Map.of("x-hub-signature-256", "sha256=invalido");
        when(sigValidator.isValid(body, "sha256=invalido")).thenReturn(false);

        MockHttp mockHttp = createMockHttp(body, headers, null, HttpStatus.FORBIDDEN);

        HttpResponseMessage res = function.callback(mockHttp.request(), context);

        verify(mockHttp.request()).createResponseBuilder(HttpStatus.FORBIDDEN);
        verifyNoInteractions(cdpService);
        assertEquals(HttpStatus.FORBIDDEN.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("POST callback procesa evento delivery 'delivered' y despacha XDM a CDP")
    void shouldProcessDeliveryStatusAndSendToCdp() {
        String wamid = "wamid.HBgLMjAyNi0xMC0wNTE=";
        String body = """
            {
              "object": "whatsapp_business_account",
              "entry": [
                {
                  "id": "100200300",
                  "changes": [
                    {
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "15551234567",
                          "phone_number_id": "100200300"
                        },
                        "statuses": [
                          {
                            "id": "wamid.HBgLMjAyNi0xMC0wNTE=",
                            "status": "delivered",
                            "timestamp": "1728100200",
                            "recipient_id": "51999888777"
                          }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
            """;

        Map<String, String> headers = new HashMap<>();
        headers.put("x-hub-signature-256", "sha256=valid_sig");
        when(sigValidator.isValid(anyString(), anyString())).thenReturn(true);

        // Simulamos documento correlacionado en Cosmos
        CorrelationDocument doc = new CorrelationDocument();
        doc.setWamid(wamid);
        doc.setCustomerId("CLI_BCP_112233");
        doc.setExecutionType("CUSTOM");
        doc.setJourneyId("journey-test-1");
        when(cosmosService.findByWamid(wamid)).thenReturn(Optional.of(doc));
        when(cdpService.publish(anyMap())).thenReturn(true);

        MockHttp mockHttp = createMockHttp(body, headers, null, HttpStatus.OK);

        HttpResponseMessage res = function.callback(mockHttp.request(), context);

        verify(cosmosService).findByWamid(wamid);
        verify(xdmMapper).buildDeliveryXdm(eq(doc), eq("delivered"), eq(wamid), eq("1728100200"), isNull(), isNull());
        verify(cdpService).publish(anyMap());
        verify(cosmosService).updateStatus(eq(wamid), eq("bcp.whatsapp.delivery.delivered"), eq("1728100200"), anyString());
        // Como es CUSTOM, NO debe llamar a ajoService.relay
        verifyNoInteractions(ajoService);

        assertEquals(HttpStatus.OK.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("POST callback reenvía a AjoWebhookService cuando executionType es NATIVE")
    void shouldRelayToAjoWhenExecutionTypeIsNative() {
        String wamid = "wamid.HBgLMjAyNi0xMC0wNTI=";
        String body = """
            {
              "object": "whatsapp_business_account",
              "entry": [
                {
                  "changes": [
                    {
                      "field": "messages",
                      "value": {
                        "statuses": [
                          {
                            "id": "wamid.HBgLMjAyNi0xMC0wNTI=",
                            "status": "read",
                            "timestamp": "1728100300"
                          }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
            """;

        Map<String, String> headers = Map.of("x-hub-signature-256", "sha256=valid_sig");
        when(sigValidator.isValid(anyString(), anyString())).thenReturn(true);

        CorrelationDocument doc = new CorrelationDocument();
        doc.setWamid(wamid);
        doc.setCustomerId("CLI_BCP_445566");
        doc.setExecutionType("NATIVE"); // Envíos nativos AJO
        when(cosmosService.findByWamid(wamid)).thenReturn(Optional.of(doc));

        MockHttp mockHttp = createMockHttp(body, headers, null, HttpStatus.OK);

        HttpResponseMessage res = function.callback(mockHttp.request(), context);

        // Se envía a CDP Y se retransmite a AJO Webhook
        verify(cdpService).publish(anyMap());
        verify(ajoService).relay(anyMap());
        assertEquals(HttpStatus.OK.value(), res.getStatusCode());
    }

    @Test
    @DisplayName("POST callback procesa interactive button_reply y despacha tracking XDM")
    void shouldProcessInteractiveButtonReply() {
        String outboundWamid = "wamid.HBgLOutbound123";
        String inboundWamid = "wamid.HBgLInbound456";

        String body = """
            {
              "object": "whatsapp_business_account",
              "entry": [
                {
                  "changes": [
                    {
                      "field": "messages",
                      "value": {
                        "messages": [
                          {
                            "from": "51999888777",
                            "id": "wamid.HBgLInbound456",
                            "timestamp": "1728100500",
                            "type": "interactive",
                            "context": {
                              "id": "wamid.HBgLOutbound123"
                            },
                            "interactive": {
                              "type": "button_reply",
                              "button_reply": {
                                "id": "btn_quiero_oferta",
                                "title": "¡Quiero la Oferta!"
                              }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
            """;

        Map<String, String> headers = Map.of("x-hub-signature-256", "sha256=valid_sig");
        when(sigValidator.isValid(anyString(), anyString())).thenReturn(true);

        CorrelationDocument doc = new CorrelationDocument();
        doc.setWamid(outboundWamid);
        doc.setCustomerId("CLI_BCP_778899");
        when(cosmosService.findByWamid(outboundWamid)).thenReturn(Optional.of(doc));

        MockHttp mockHttp = createMockHttp(body, headers, null, HttpStatus.OK);

        HttpResponseMessage res = function.callback(mockHttp.request(), context);

        verify(xdmMapper).buildTrackingXdm(
            eq(doc),
            eq(outboundWamid),
            eq(inboundWamid),
            eq("1728100500"),
            eq("button_reply"),
            eq("¡Quiero la Oferta!"),
            eq("btn_quiero_oferta"),
            eq("¡Quiero la Oferta!")
        );
        verify(cdpService).publish(anyMap());
        assertEquals(HttpStatus.OK.value(), res.getStatusCode());
    }
}

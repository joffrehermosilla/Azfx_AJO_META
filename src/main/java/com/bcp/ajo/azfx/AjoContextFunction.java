package com.bcp.ajo.azfx;

import com.bcp.ajo.azfx.application.service.StoreAjoContextService;
import com.bcp.ajo.azfx.domain.model.AjoContextCommand;
import com.bcp.ajo.azfx.domain.port.in.StoreAjoContextUseCase;
import com.bcp.ajo.azfx.domain.port.out.CorrelationRepositoryPort;
import com.bcp.ajo.azfx.model.AjoContextRequest;
import com.bcp.ajo.azfx.model.CorrelationDocument;
import com.bcp.ajo.azfx.service.CosmosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AZURE FUNCTION 1 — AjoContextFunction (Driving HTTP Adapter)
 * Endpoint: POST /api/ajo-context
 * ══════════════════════════════════════════════════════════════════════════
 */
public class AjoContextFunction {

    private static final Logger log = LoggerFactory.getLogger(AjoContextFunction.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    // Puerto de Entrada (Caso de uso)
    private final StoreAjoContextUseCase storeUseCase;

    public AjoContextFunction() {
        this(new StoreAjoContextService(new CosmosService()));
    }

    public AjoContextFunction(CorrelationRepositoryPort repository) {
        this(new StoreAjoContextService(repository));
    }

    public AjoContextFunction(StoreAjoContextUseCase storeUseCase) {
        this.storeUseCase = storeUseCase;
    }

    @FunctionName("ajo-context")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = { HttpMethod.POST },
            authLevel = AuthorizationLevel.FUNCTION,
            route = "ajo-context"
        )
        HttpRequestMessage<Optional<String>> request,
        ExecutionContext context
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[FUNCTION1] ajo-context invocado. Invocation ID: {}", context.getInvocationId());

        try {
            // ── 1. Parsear body ──────────────────────────────────────────────
            String rawBody = request.getBody().orElse("").trim();
            if (rawBody.isEmpty()) {
                log.warn("[FUNCTION1] Body vacío — rechazado.");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Body requerido\"}")
                    .build();
            }

            AjoContextRequest req = MAPPER.readValue(rawBody, AjoContextRequest.class);

            // ── 2. Validar campos obligatorios ───────────────────────────────
            if (req.getWamid() == null || req.getWamid().isBlank()) {
                log.warn("[FUNCTION1] wamid ausente — rechazado.");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Campo 'wamid' es obligatorio\"}")
                    .build();
            }

            if (req.getJourneyId() == null || req.getJourneyId().isBlank()) {
                log.warn("[FUNCTION1] journeyId ausente — rechazado. wamid={}", req.getWamid());
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Campo 'journeyId' es obligatorio\"}")
                    .build();
            }

            // ── 3. Mapeo a Command y ejecución del Caso de Uso (Hexagonal) ───
            AjoContextCommand command = new AjoContextCommand(
                req.getWamid(),
                req.getProfileId(),
                req.getRecipient(),
                req.getWaId(),
                req.getMessageStatus(),
                req.getNamespace(),
                req.getJourneyId(),
                req.getJourneyVersionId(),
                req.getJourneyVersionName(),
                req.getJourneyInstanceId(),
                req.getJourneyNodeId(),
                req.getJourneyNodeName(),
                req.getJourneyActionId(),
                req.getJourneyActionName(),
                req.getTemplateName(),
                req.getTemplateCategory(),
                req.getSandboxName(),
                req.getType()
            );

            CorrelationDocument doc = storeUseCase.execute(command);

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[FUNCTION1] OK — wamid={} correlationId={} elapsed={}ms",
                doc.getWamid(), doc.getCorrelationId(), elapsedMs);

            Map<String, Object> responseBody = Map.of(
                "correlationId", doc.getCorrelationId(),
                "wamid",         doc.getWamid(),
                "status",        doc.getCorrelationStatus(),
                "elapsedMs",     elapsedMs
            );

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", doc.getCorrelationId())
                .body(MAPPER.writeValueAsString(responseBody))
                .build();

        } catch (Exception e) {
            log.error("[FUNCTION1] Error no manejado: {}", e.getMessage(), e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"Error interno: " + e.getMessage().replace("\"", "'") + "\"}")
                .build();
        }
    }
}

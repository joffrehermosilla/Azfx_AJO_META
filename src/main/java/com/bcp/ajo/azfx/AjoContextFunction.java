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

import java.util.Map;
import java.util.Optional;

/**
 * Function 1: receives AJO Custom Action 2 context and stores it in Cosmos DB.
 */
public class AjoContextFunction {
    private static final Logger log = LoggerFactory.getLogger(AjoContextFunction.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
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
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.FUNCTION, route = "ajo-context") HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        long startMs = System.currentTimeMillis();
        log.info("[FUNCTION1] ajo-context invocado. Invocation ID={}", context.getInvocationId());
        try {
            String rawBody = request.getBody().orElse("").trim();
            if (rawBody.isEmpty()) {
                return json(request, HttpStatus.BAD_REQUEST, "{\"error\":\"Body requerido\"}");
            }

            AjoContextRequest req = MAPPER.readValue(rawBody, AjoContextRequest.class);
            if (isBlank(req.getWamid())) {
                return json(request, HttpStatus.BAD_REQUEST, "{\"error\":\"Campo wamid es obligatorio\"}");
            }
            if (isBlank(req.getJourneyId())) {
                return json(request, HttpStatus.BAD_REQUEST, "{\"error\":\"Campo journeyId es obligatorio\"}");
            }

            AjoContextCommand command = new AjoContextCommand(
                    req.getWamid(), req.getProfileId(), req.getRecipient(), req.getWaId(),
                    req.getMessageStatus(), req.getNamespace(), req.getJourneyId(),
                    req.getJourneyVersionId(), req.getJourneyVersionName(), req.getJourneyInstanceId(),
                    req.getJourneyNodeId(), req.getJourneyNodeName(), req.getJourneyActionId(),
                    req.getJourneyActionName(), req.getTemplateName(), req.getTemplateCategory(),
                    req.getSandboxName(), req.getType(), req.getCampaignId(), req.getTemplateWamId(),
                    req.getMessageProfileId(), req.getIsTestExecution());

            CorrelationDocument doc = storeUseCase.execute(command);
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[FUNCTION1] Contexto almacenado — wamid={} elapsed={}ms",
                    safeId(doc.getWamid()), elapsedMs);

            Map<String, Object> response = Map.of(
                    "correlationId", doc.getCorrelationId(),
                    "wamid", doc.getWamid(),
                    "status", doc.getCorrelationStatus(),
                    "elapsedMs", elapsedMs);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-Id", doc.getCorrelationId())
                    .body(MAPPER.writeValueAsString(response))
                    .build();
        } catch (Exception e) {
            log.error("[FUNCTION1] Error guardando contexto AJO: {}", e.getMessage(), e);
            return json(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "{\"error\":\"No se pudo almacenar el contexto\"}");
        }
    }

    private static HttpResponseMessage json(HttpRequestMessage<?> request, HttpStatus status, String body) {
        return request.createResponseBuilder(status).header("Content-Type", "application/json").body(body).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeId(String value) {
        return isBlank(value) ? "UNKNOWN" : value.length() <= 18 ? value : value.substring(0, 12) + "…";
    }
}

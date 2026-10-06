package com.bcp.ajo.azfx;

import com.bcp.ajo.azfx.application.service.ProcessMetaWebhookService;
import com.bcp.ajo.azfx.application.service.VerifyMetaWebhookService;
import com.bcp.ajo.azfx.domain.model.ProcessingResult;
import com.bcp.ajo.azfx.domain.model.WebhookChallenge;
import com.bcp.ajo.azfx.domain.port.in.ProcessMetaWebhookUseCase;
import com.bcp.ajo.azfx.domain.port.in.VerifyMetaWebhookUseCase;
import com.bcp.ajo.azfx.service.AjoWebhookService;
import com.bcp.ajo.azfx.service.CdpService;
import com.bcp.ajo.azfx.service.CosmosService;
import com.bcp.ajo.azfx.service.XdmMapper;
import com.bcp.ajo.azfx.util.MetaSignatureValidator;
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

import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AZURE FUNCTION 2 — MetaWebhookFunction (Driving HTTP Adapter)
 * Endpoints:
 *   GET  /api/meta-webhook  → Verificación de Meta (hub.challenge)
 *   POST /api/meta-webhook  → Callbacks de Meta (delivery y tracking)
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Arquitectura Hexagonal:
 *   - Adaptador Primario (Driving) para Meta Webhooks.
 *   - GET delega a VerifyMetaWebhookUseCase.
 *   - POST delega a ProcessMetaWebhookUseCase.
 * ══════════════════════════════════════════════════════════════════════════
 */
public class MetaWebhookFunction {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookFunction.class);

    private final VerifyMetaWebhookUseCase verifyUseCase;
    private final ProcessMetaWebhookUseCase processUseCase;

    public MetaWebhookFunction() {
        this(
            new VerifyMetaWebhookService(System.getenv("META_VERIFY_TOKEN")),
            new ProcessMetaWebhookService(
                new MetaSignatureValidator(),
                new CosmosService(),
                new CdpService(),
                new AjoWebhookService(),
                new XdmMapper()
            )
        );
    }

    public MetaWebhookFunction(
        VerifyMetaWebhookUseCase verifyUseCase,
        ProcessMetaWebhookUseCase processUseCase
    ) {
        this.verifyUseCase  = verifyUseCase;
        this.processUseCase = processUseCase;
    }

    // Constructor de conveniencia compatible con inyecciones directas de adaptadores
    public MetaWebhookFunction(
        CosmosService cosmosService,
        CdpService cdpService,
        AjoWebhookService ajoService,
        XdmMapper xdmMapper,
        MetaSignatureValidator sigValidator,
        String verifyToken
    ) {
        this(
            new VerifyMetaWebhookService(verifyToken),
            new ProcessMetaWebhookService(sigValidator, cosmosService, cdpService, ajoService, xdmMapper)
        );
    }

    public MetaWebhookFunction(
        CosmosService cosmosService,
        CdpService cdpService,
        AjoWebhookService ajoService,
        XdmMapper xdmMapper,
        MetaSignatureValidator sigValidator
    ) {
        this(cosmosService, cdpService, ajoService, xdmMapper, sigValidator, System.getenv("META_VERIFY_TOKEN"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET — Verificación de Meta (Handshake)
    // ═══════════════════════════════════════════════════════════════════════
    @FunctionName("meta-webhook-verify")
    public HttpResponseMessage verify(
        @HttpTrigger(
            name = "req",
            methods = { HttpMethod.GET },
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "meta-webhook"
        )
        HttpRequestMessage<Optional<String>> request,
        ExecutionContext context
    ) {
        log.info("[FUNCTION2] GET /meta-webhook — Verificación de Meta.");

        WebhookChallenge challenge = new WebhookChallenge(
            request.getQueryParameters().get("hub.mode"),
            request.getQueryParameters().get("hub.verify_token"),
            request.getQueryParameters().get("hub.challenge")
        );

        return verifyUseCase.execute(challenge)
            .map(code -> request.createResponseBuilder(HttpStatus.OK).body(code).build())
            .orElseGet(() -> {
                log.warn("[FUNCTION2] Verificación FALLIDA.");
                return request.createResponseBuilder(HttpStatus.FORBIDDEN).body("Forbidden").build();
            });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST — Callbacks de Meta (Delivery y Tracking)
    // ═══════════════════════════════════════════════════════════════════════
    @FunctionName("meta-webhook-callback")
    public HttpResponseMessage callback(
        @HttpTrigger(
            name = "req",
            methods = { HttpMethod.POST },
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "meta-webhook"
        )
        HttpRequestMessage<Optional<String>> request,
        ExecutionContext context
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[FUNCTION2] POST /meta-webhook — Invocation ID: {}", context.getInvocationId());

        String rawBody = request.getBody().orElse("").trim();
        String signature = request.getHeaders().getOrDefault(
            "x-hub-signature-256",
            request.getHeaders().get("X-Hub-Signature-256")
        );

        ProcessingResult result = processUseCase.execute(rawBody, signature);

        if (!result.success() && "Firma inválida".equals(result.message())) {
            return request.createResponseBuilder(HttpStatus.FORBIDDEN)
                .body("Forbidden")
                .build();
        }

        if (!result.success()) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"" + result.message() + "\"}")
                .build();
        }

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[FUNCTION2] Procesado OK — items={} elapsed={}ms", result.itemsProcessed(), elapsed);

        return request.createResponseBuilder(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body("{\"status\":\"OK\",\"processed\":" + result.itemsProcessed() + "}")
            .build();
    }
}

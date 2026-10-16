package com.bcp.ajo.azfx.service;

import com.bcp.ajo.azfx.domain.port.out.AjoRelayPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * AjoWebhookService: adaptador de salida (Driven Adapter) para AJO Webhook
 * Ingest API.
 * Implementa AjoRelayPort. Se usa solo para envios NATIVE.
 * Meta -> Azure -> AJO Webhook -> datasets nativos de AJO/CDP.
 */
public class AjoWebhookService implements AjoRelayPort {

    private static final Logger log = LoggerFactory.getLogger(AjoWebhookService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String ajoWebhookUrl;
    private final String ajoBearerToken;

    public AjoWebhookService() {
        this.ajoWebhookUrl = System.getenv("AJO_WEBHOOK_URL");
        this.ajoBearerToken = System.getenv("AJO_BEARER_TOKEN"); // opcional
    }

    @Override
    public boolean relay(Map<String, Object> metaPayload) {
        if (ajoWebhookUrl == null || ajoWebhookUrl.isBlank()) {
            log.error("[AJO] AJO_WEBHOOK_URL no configurada. Relay no ejecutado.");
            return false;
        }

        try {
            String body = MAPPER.writeValueAsString(metaPayload);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ajoWebhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (ajoBearerToken != null && !ajoBearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + ajoBearerToken);
            }

            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();

            if (code >= 200 && code < 300) {
                log.info("[AJO] Relay OK, status={}", code);
                return true;
            }
            log.warn("[AJO] Relay con respuesta no 2xx, status={} body={}", code, response.body());
            return false;

        } catch (Exception e) {
            log.error("[AJO] Exception en relay, error={}", e.getMessage(), e);
            return false;
        }
    }
}

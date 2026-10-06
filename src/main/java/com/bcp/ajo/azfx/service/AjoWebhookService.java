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
 * AjoWebhookService — Adaptador de salida (Driven Adapter) para AJO Webhook Ingest API.
 * Implementa el puerto AjoRelayPort.
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
        this.ajoWebhookUrl   = System.getenv("AJO_WEBHOOK_URL");
        this.ajoBearerToken  = System.getenv("AJO_BEARER_TOKEN");
    }

    /**
     * Reenvía el payload de Meta al AJO Webhook API.
     * Se usa para el camino NATIVE: Meta → Azure → AJO Webhook → datasets nativos.
     *
     * @param metaPayload Map con el payload original de Meta (sin modificaciones).
     * @return true si AJO respondió 200/204.
     */
    public boolean relay(Map<String, Object> metaPayload) {
        if (ajoWebhookUrl == null || ajoWebhookUrl.isBlank()) {
            log.error("[AJO] AJO_WEBHOOK_URL no configurada. Relay no ejecutado.");
            return false;
        }

        try {
            String body = MAPPER.writeValueAsString(metaPayload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ajoWebhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ajoBearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("[AJO] Relay OK — status={}", response.statusCode());
                return true;
            } else {
                log.warn("[AJO] Relay respuesta no 200 — status={} body={}",
                    response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            log.error("[AJO] Exception en relay — error={}", e.getMessage(), e);
            return false;
        }
    }
}

package com.bcp.ajo.azfx.service;

import com.bcp.ajo.azfx.domain.port.out.CdpPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * CdpService — Adaptador de salida (Driven Adapter) para Adobe CDP (AEP Streaming Ingestion).
 * Implementa el puerto CdpPublisherPort.
 */
public class CdpService implements CdpPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(CdpService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String cdpIngestUrl;
    private final String cdpFlowId;
    private final String cdpBearerToken;

    public CdpService() {
        this.cdpIngestUrl   = System.getenv("CDP_INGEST_URL");
        this.cdpFlowId      = System.getenv("CDP_FLOW_ID");
        this.cdpBearerToken = System.getenv("CDP_BEARER_TOKEN");
    }

    @Override
    public boolean publish(Map<String, Object> xdmEvent) {
        return sendEvent(xdmEvent);
    }

    /**
     * Envía el XDM event a AEP Streaming Ingestion.
     *
     * @param xdmEvent Map con el XDM ExperienceEvent construido por XdmMapper.
     * @return true si CDP respondió 200, false en caso contrario.
     */
    public boolean sendEvent(Map<String, Object> xdmEvent) {
        if (cdpIngestUrl == null || cdpIngestUrl.isBlank()) {
            log.error("[CDP] CDP_INGEST_URL no configurada. Evento no enviado.");
            return false;
        }

        try {
            String body = MAPPER.writeValueAsString(xdmEvent);

            // Validar tamaño del payload (1 MB máximo)
            if (body.getBytes().length > 1_048_576) {
                log.error("[CDP] Payload supera 1 MB — se descarta. wamid={}",
                    extractWamidFromXdm(xdmEvent));
                return false;
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(cdpIngestUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

            // Header de Flow ID (AEP Streaming)
            if (cdpFlowId != null && !cdpFlowId.isBlank()) {
                reqBuilder.header("x-adobe-flow-id", cdpFlowId);
            }

            // Bearer token de autenticación
            if (cdpBearerToken != null && !cdpBearerToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + cdpBearerToken);
            }

            HttpResponse<String> response = HTTP.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("[CDP] Evento enviado OK — status={} eventType={}",
                    response.statusCode(), xdmEvent.get("eventType"));
                return true;
            } else {
                log.error("[CDP] Error HTTP — status={} body={}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            log.error("[CDP] Exception enviando evento — error={}", e.getMessage(), e);
            return false;
        }
    }

    private String extractWamidFromXdm(Map<String, Object> xdm) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
            @SuppressWarnings("unchecked")
            Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
            @SuppressWarnings("unchecked")
            Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) whatsapp.get("message");
            return String.valueOf(message.get("wamId"));
        } catch (Exception e) {
            return "unknown";
        }
    }
}

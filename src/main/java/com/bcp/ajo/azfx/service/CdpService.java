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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de salida hacia el inlet de Adobe CDP (DCS). Implementa
 * CdpPublisherPort.
 */
public class CdpService implements CdpPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(CdpService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final int MAX_BYTES = 1_048_576;

    private final String cdpIngestUrl;
    private final String cdpFlowId;
    private final String cdpBearerToken;
    private final String sandboxName;
    private final String sandboxHeader;

    public CdpService() {
        this.cdpIngestUrl = System.getenv("CDP_INGEST_URL");
        this.cdpFlowId = System.getenv("CDP_FLOW_ID");
        this.cdpBearerToken = System.getenv("CDP_BEARER_TOKEN");
        this.sandboxName = System.getenv("CDP_SANDBOX_NAME"); // ej: dev
        String h = System.getenv("CDP_SANDBOX_HEADER"); // nombre del header, configurable
        this.sandboxHeader = (h == null || h.isBlank()) ? "x-sandbox-name" : h;
    }

    @Override
    public boolean publish(Map<String, Object> xdmEvent) {
        return sendEvent(xdmEvent);
    }

    public boolean sendEvent(Map<String, Object> xdmEvent) {
        if (cdpIngestUrl == null || cdpIngestUrl.isBlank()) {
            log.error("[CDP] CDP_INGEST_URL no configurada. Evento no enviado.");
            return false;
        }
        try {
            // Se quitan los null para no enviar claves vacías al schema.
            String body = MAPPER.writeValueAsString(pruneNulls(xdmEvent));

            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                log.error("[CDP] Payload supera 1 MB, se descarta. wamid={}", extractWamid(xdmEvent));
                return false;
            }

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(cdpIngestUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (cdpFlowId != null && !cdpFlowId.isBlank()) {
                req.header("x-adobe-flow-id", cdpFlowId);
            }
            if (sandboxName != null && !sandboxName.isBlank()) {
                req.header(sandboxHeader, sandboxName);
            }
            if (cdpBearerToken != null && !cdpBearerToken.isBlank()) {
                req.header("Authorization", "Bearer " + cdpBearerToken);
            }

            HttpResponse<String> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();

            if (code >= 200 && code < 300) { // AEP puede responder 200 o 202
                log.info("[CDP] Evento enviado OK, status={} eventName={} wamid={}",
                        code, extractEventName(xdmEvent), extractWamid(xdmEvent));
                return true;
            }
            log.error("[CDP] Error HTTP, status={} body={}", code, res.body());
            return false;

        } catch (Exception e) {
            log.error("[CDP] Exception enviando evento, error={}", e.getMessage(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object pruneNulls(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> {
                if (v != null)
                    out.put(k, pruneNulls(v));
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list)
                if (o != null)
                    out.add(pruneNulls(o));
            return out;
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static String extractWamid(Map<String, Object> xdm) {
        try {
            Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
            Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
            Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");
            Map<String, Object> message = (Map<String, Object>) whatsapp.get("message");
            return String.valueOf(message.get("wamId"));
        } catch (Exception e) {
            return "unknown";
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractEventName(Map<String, Object> xdm) {
        try {
            Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
            Map<String, Object> event = (Map<String, Object>) bcp.get("event");
            return String.valueOf(event.get("name"));
        } catch (Exception e) {
            return "unknown";
        }
    }
}
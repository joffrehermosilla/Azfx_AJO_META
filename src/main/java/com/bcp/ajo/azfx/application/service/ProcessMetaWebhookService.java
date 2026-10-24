package com.bcp.ajo.azfx.application.service;

import com.bcp.ajo.azfx.domain.model.DeliveryEvent;
import com.bcp.ajo.azfx.domain.model.ProcessingResult;
import com.bcp.ajo.azfx.domain.model.TrackingEvent;
import com.bcp.ajo.azfx.domain.port.in.ProcessMetaWebhookUseCase;
import com.bcp.ajo.azfx.domain.port.out.AjoRelayPort;
import com.bcp.ajo.azfx.domain.port.out.CdpPublisherPort;
import com.bcp.ajo.azfx.domain.port.out.CorrelationRepositoryPort;
import com.bcp.ajo.azfx.domain.port.out.SignatureValidatorPort;
import com.bcp.ajo.azfx.model.CorrelationDocument;
import com.bcp.ajo.azfx.service.XdmMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Orquesta los callbacks de Meta con ruteo EXCLUSIVO segun executionType:
 * NATIVE -> relay del payload crudo a AJO Webhook (tablas nativas de AJO/CDP).
 * No va al endpoint CDP custom.
 * CUSTOM -> XDM BCP al endpoint CDP (tambien si el WAMID no esta
 * correlacionado).
 * Si algun destino falla se devuelve error para que Meta reintente.
 */
public class ProcessMetaWebhookService implements ProcessMetaWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessMetaWebhookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SignatureValidatorPort signatureValidator;
    private final CorrelationRepositoryPort correlationRepo;
    private final CdpPublisherPort cdpPublisher;
    private final AjoRelayPort ajoRelay;
    private final XdmMapper xdmMapper;

    /** Contadores mutables del procesamiento de UN callback. */
    private static final class Stats {
        int handled;
        int cdpOk;
        int failures;
        boolean relayNeeded;
    }

    public ProcessMetaWebhookService(
            SignatureValidatorPort signatureValidator,
            CorrelationRepositoryPort correlationRepo,
            CdpPublisherPort cdpPublisher,
            AjoRelayPort ajoRelay,
            XdmMapper xdmMapper) {
        this.signatureValidator = Objects.requireNonNull(signatureValidator, "signatureValidator required");
        this.correlationRepo = Objects.requireNonNull(correlationRepo, "correlationRepo required");
        this.cdpPublisher = Objects.requireNonNull(cdpPublisher, "cdpPublisher required");
        this.ajoRelay = Objects.requireNonNull(ajoRelay, "ajoRelay required");
        this.xdmMapper = Objects.requireNonNull(xdmMapper, "xdmMapper required");
    }

    @Override
    public ProcessingResult execute(String rawBody, String signatureHeader) {
        if (!signatureValidator.isValid(rawBody, signatureHeader)) {
            log.warn("[USE-CASE] Firma HMAC-SHA256 rechazada");
            return ProcessingResult.unauthorized("Firma inválida");
        }

        try {
            Map<String, Object> payload = MAPPER.readValue(rawBody, new TypeReference<>() {
            });
            Stats stats = new Stats();

            for (Map<String, Object> value : extractValues(payload)) {
                processDeliveries(value, stats);
                processTrackings(value, stats);
            }

            // Un solo relay por callback (evita duplicar si el payload trae varios statuses
            // NATIVE).
            if (stats.relayNeeded) {
                boolean relayed = ajoRelay.relay(payload);
                if (relayed) {
                    log.info("[AJO] Relay nativo OK");
                } else {
                    stats.failures++;
                    log.error("[AJO] Relay nativo FALLO");
                }
            }

            if (stats.failures > 0) {
                String message = "Downstream failed for " + stats.failures + " destination(s); "
                        + stats.cdpOk + " event(s) accepted by CDP";
                log.error("[USE-CASE] {}", message);
                return new ProcessingResult(false, stats.handled, message);
            }

            log.info("[USE-CASE] Callback procesado, handled={} cdpAccepted={} relayedNative={}",
                    stats.handled, stats.cdpOk, stats.relayNeeded);
            return ProcessingResult.ok(stats.handled);

        } catch (Exception e) {
            log.error("[USE-CASE] Error procesando payload de Meta: {}", e.getMessage(), e);
            return ProcessingResult.error("Error procesando callback de Meta");
        }
    }

    private void processDeliveries(Map<String, Object> value, Stats stats) {
        for (Map<String, Object> status : extractList(value, "statuses")) {
            DeliveryEvent event = toDeliveryEvent(status);
            if (isBlank(event.wamid())) {
                continue;
            }
            log.info("[DELIVERY] wamid={} status={}", safeId(event.wamid()), event.status());

            CorrelationDocument doc = findWithRetry(event.wamid());
            if (doc == null) {
                log.warn("[DELIVERY] UNCORRELATED (NATIVE) wamid={} status={} -> Relay a AJO",
                        safeId(event.wamid()), event.status());
                stats.relayNeeded = true;
                stats.handled++;
                continue;
            }

            if (isNative(doc)) {
                log.info("[ROUTE] NATIVE -> AJO relay, wamid={}", safeId(event.wamid()));
                stats.relayNeeded = true;
            } else {
                log.info("[ROUTE] CUSTOM -> CDP, wamid={}", safeId(event.wamid()));
                Map<String, Object> xdm = xdmMapper.buildDeliveryXdm(
                        doc, event.status(), event.wamid(), event.metaTimestamp(),
                        event.errorCode(), event.errorMessage());
                if (cdpPublisher.publish(xdm)) {
                    stats.cdpOk++;
                } else {
                    stats.failures++;
                    log.error("[CDP] Delivery no confirmado, wamid={} status={}",
                            safeId(event.wamid()), event.status());
                }
            }

            String eventType = "bcp.whatsapp.delivery."
                    + (event.status() != null ? event.status().toLowerCase(Locale.ROOT) : "unknown");
            correlationRepo.updateStatus(event.wamid(), eventType,
                    event.metaTimestamp(), Instant.now().toString());
            stats.handled++;
        }
    }

    private void processTrackings(Map<String, Object> value, Stats stats) {
        for (Map<String, Object> message : extractList(value, "messages")) {
            TrackingEvent event = toTrackingEvent(message);
            if (event == null) {
                continue;
            }

            // La clave de correlacion es el WAMID outbound (context.id), nunca el inbound.
            CorrelationDocument doc = !isBlank(event.wamidOutbound())
                    ? correlationRepo.findByWamid(event.wamidOutbound()).orElse(null)
                    : null;

            if (doc == null && isBlank(event.payloadCustomerId())) {
                log.info("[ROUTE] NATIVE (uncorrelated reply) -> AJO relay, wamidIn={}",
                        safeId(event.wamidInbound()));
                stats.relayNeeded = true;
                stats.handled++;
                continue;
            }

            log.info("[TRACKING] wamidOut={} wamidIn={} type={} correlated={}",
                    safeId(event.wamidOutbound()), safeId(event.wamidInbound()),
                    event.trackingType(), doc != null);

            if (isNative(doc)) {
                log.info("[ROUTE] NATIVE -> AJO relay (tracking), wamidOut={}", safeId(event.wamidOutbound()));
                stats.relayNeeded = true;
                stats.handled++;
                continue;
            }

            Map<String, Object> xdm;
            if (!isBlank(event.payloadCustomerId()) || !isBlank(event.payloadTemplateName())) {
                xdm = xdmMapper.buildTrackingXdm(
                        doc, event.wamidOutbound(), event.wamidInbound(), event.metaTimestamp(),
                        event.trackingType(), event.reply(), event.buttonId(), event.buttonTitle(),
                        event.payloadCustomerId(), event.payloadTemplateName());
            } else {
                xdm = xdmMapper.buildTrackingXdm(
                        doc, event.wamidOutbound(), event.wamidInbound(), event.metaTimestamp(),
                        event.trackingType(), event.reply(), event.buttonId(), event.buttonTitle());
            }

            log.info("[ROUTE] CUSTOM -> CDP (tracking), wamidOut={}", safeId(event.wamidOutbound()));
            if (cdpPublisher.publish(xdm)) {
                stats.cdpOk++;
            } else {
                stats.failures++;
                log.error("[CDP] Tracking no confirmado, wamidIn={} type={}",
                        safeId(event.wamidInbound()), event.trackingType());
            }
            stats.handled++;
        }
    }

    /**
     * Meta puede enviar "sent"/"delivered" antes de que la Custom Action 2 termine
     * de guardar el contexto.
     * Reintenta el lookup unos segundos solo cuando el documento aun no existe.
     */
    private CorrelationDocument findWithRetry(String wamid) {
        final int attempts = 4;
        for (int i = 1; i <= attempts; i++) {
            Optional<CorrelationDocument> found = correlationRepo.findByWamid(wamid);
            if (found != null && found.isPresent()) {
                if (i > 1) {
                    log.info("[DELIVERY] Correlacion encontrada en el intento {} wamid={}", i, safeId(wamid));
                }
                return found.get();
            }
            if (i < attempts) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    private static boolean isNative(CorrelationDocument doc) {
        return doc != null && "NATIVE".equalsIgnoreCase(doc.getExecutionType());
    }

    // Parsing de payload de Meta

    private DeliveryEvent toDeliveryEvent(Map<String, Object> status) {
        String wamid = getString(status, "id");
        String state = getString(status, "status");
        String metaTimestamp = getString(status, "timestamp");

        Map<String, Object> firstError = firstError(status.get("errors"));
        String errorCode = firstError != null ? getString(firstError, "code") : null;
        String errorMessage = firstError != null
                ? firstNonBlank(getString(firstError, "title"), getString(firstError, "message"))
                : null;

        return new DeliveryEvent(wamid, state, metaTimestamp, errorCode, errorMessage);
    }

    private TrackingEvent toTrackingEvent(Map<String, Object> message) {
        String type = getString(message, "type");
        String wamidIn = getString(message, "id");
        String metaTimestamp = getString(message, "timestamp");
        Map<String, Object> context = getMap(message, "context");
        String wamidOutbound = context != null ? getString(context, "id") : null;

        if ("interactive".equals(type)) {
            Map<String, Object> interactive = getMap(message, "interactive");
            if (interactive != null && "button_reply".equals(getString(interactive, "type"))) {
                Map<String, Object> buttonReply = getMap(interactive, "button_reply");
                return new TrackingEvent(
                        wamidOutbound, wamidIn, metaTimestamp, "button_reply",
                        buttonReply != null ? getString(buttonReply, "title") : null,
                        buttonReply != null ? getString(buttonReply, "id") : null,
                        buttonReply != null ? getString(buttonReply, "title") : null);
            }
        }

        if ("text".equals(type)) {
            Map<String, Object> text = getMap(message, "text");
            return new TrackingEvent(wamidOutbound, wamidIn, metaTimestamp, "text_reply",
                    text != null ? getString(text, "body") : null, null, null);
        }

        // Quick Reply:
        // {"type":"button","button":{"payload":"cid=...|tn=...","text":"..."}}
        if ("button".equals(type)) {
            Map<String, Object> button = getMap(message, "button");
            if (button == null) {
                return null;
            }
            Map<String, String> metadata = parseButtonPayload(getString(button, "payload"));
            return new TrackingEvent(
                    wamidOutbound, wamidIn, metaTimestamp, "button_reply",
                    getString(button, "text"), null, getString(button, "text"),
                    metadata.get("cid"), metadata.get("tn"));
        }
        return null;
    }

    private Map<String, Object> firstError(Object errors) {
        if (errors instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) first;
            return result;
        }
        if (errors instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }
        return null;
    }

    private List<Map<String, Object>> extractValues(Map<String, Object> payload) {
        Object rawEntry = payload.get("entry");
        if (!(rawEntry instanceof List<?> entries)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entryObject : entries) {
            if (!(entryObject instanceof Map<?, ?> entry)) {
                continue;
            }
            Object rawChanges = entry.get("changes");
            if (!(rawChanges instanceof List<?> changes)) {
                continue;
            }
            for (Object changeObject : changes) {
                if (!(changeObject instanceof Map<?, ?> change)) {
                    continue;
                }
                Object rawValue = change.get("value");
                if (rawValue instanceof Map<?, ?> value) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) value;
                    result.add(typed);
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> extractList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> mapItem) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) mapItem;
                result.add(typed);
            }
        }
        return result;
    }

    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) raw;
            return typed;
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, String> parseButtonPayload(String payload) {
        if (isBlank(payload)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : payload.split("\\|")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                result.putIfAbsent(pair[0].trim(), pair[1].trim());
            }
        }
        return result;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeId(String value) {
        if (isBlank(value)) {
            return "UNKNOWN";
        }
        return value.length() <= 18 ? value : value.substring(0, 12) + "…";
    }
}
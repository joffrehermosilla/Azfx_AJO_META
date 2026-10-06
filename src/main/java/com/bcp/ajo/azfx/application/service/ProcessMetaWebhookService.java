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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * ProcessMetaWebhookService — Orquesta de forma funcional el procesamiento de callbacks de Meta.
 * Procesa colecciones de eventos mediante flujos funcionales (Streams) y desacopla la persistencia,
 * transporte XDM y reenvío nativo a través de puertos.
 */
public class ProcessMetaWebhookService implements ProcessMetaWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessMetaWebhookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SignatureValidatorPort signatureValidator;
    private final CorrelationRepositoryPort correlationRepo;
    private final CdpPublisherPort cdpPublisher;
    private final AjoRelayPort ajoRelay;
    private final XdmMapper xdmMapper;

    public ProcessMetaWebhookService(
        SignatureValidatorPort signatureValidator,
        CorrelationRepositoryPort correlationRepo,
        CdpPublisherPort cdpPublisher,
        AjoRelayPort ajoRelay,
        XdmMapper xdmMapper
    ) {
        this.signatureValidator = Objects.requireNonNull(signatureValidator, "signatureValidator required");
        this.correlationRepo   = Objects.requireNonNull(correlationRepo, "correlationRepo required");
        this.cdpPublisher       = Objects.requireNonNull(cdpPublisher, "cdpPublisher required");
        this.ajoRelay           = Objects.requireNonNull(ajoRelay, "ajoRelay required");
        this.xdmMapper          = Objects.requireNonNull(xdmMapper, "xdmMapper required");
    }

    @Override
    public ProcessingResult execute(String rawBody, String signatureHeader) {
        // 1. Verificación funcional de firma HMAC-SHA256
        if (!signatureValidator.isValid(rawBody, signatureHeader)) {
            log.warn("[USE-CASE] Firma HMAC-SHA256 rechazada");
            return ProcessingResult.unauthorized("Firma inválida");
        }

        try {
            Map<String, Object> payload = MAPPER.readValue(rawBody, new TypeReference<>() {});
            AtomicInteger processedCount = new AtomicInteger(0);

            // 2. Procesamiento reactivo/funcional de entry[] y changes[]
            extractValues(payload).forEach(value -> {
                processDeliveries(value, payload, processedCount::incrementAndGet);
                processTrackings(value, processedCount::incrementAndGet);
            });

            return ProcessingResult.ok(processedCount.get());

        } catch (Exception e) {
            log.error("[USE-CASE] Error procesando payload de Meta: {}", e.getMessage(), e);
            return ProcessingResult.error(e.getMessage());
        }
    }

    // ── Pipeline funcional para Delivery (statuses[]) ────────────────────────
    private void processDeliveries(Map<String, Object> value, Map<String, Object> fullPayload, Runnable onProcessed) {
        extractList(value, "statuses").stream()
            .map(this::toDeliveryEvent)
            .forEach(event -> {
                log.info("[DELIVERY] wamid={} status={}", event.wamid(), event.status());

                // Lookup en Cosmos mediante puerto
                Optional<CorrelationDocument> correlationOpt = correlationRepo.findByWamid(event.wamid());
                CorrelationDocument doc = correlationOpt.orElse(null);

                if (doc == null) {
                    log.warn("[DELIVERY] UNCORRELATED — wamid={} status={}", event.wamid(), event.status());
                }

                // Generar XDM ExperienceEvent
                Map<String, Object> xdm = xdmMapper.buildDeliveryXdm(
                    doc, event.status(), event.wamid(), event.metaTimestamp(),
                    event.errorCode(), event.errorMessage()
                );
                cdpPublisher.publish(xdm);

                // Relay condicional si executionType == "NATIVE"
                correlationOpt
                    .filter(c -> "NATIVE".equalsIgnoreCase(c.getExecutionType()))
                    .ifPresent(c -> {
                        log.info("[DELIVERY] executionType=NATIVE — relay a AJO Webhook");
                        ajoRelay.relay(fullPayload);
                    });

                // Actualizar estado en Cosmos
                String eventType = "bcp.whatsapp.delivery." + (event.status() != null ? event.status() : "unknown");
                correlationRepo.updateStatus(event.wamid(), eventType, event.metaTimestamp(), Instant.now().toString());

                onProcessed.run();
            });
    }

    // ── Pipeline funcional para Tracking (messages[]) ────────────────────────
    private void processTrackings(Map<String, Object> value, Runnable onProcessed) {
        extractList(value, "messages").stream()
            .map(this::toTrackingEvent)
            .filter(Objects::nonNull)
            .forEach(event -> {
                log.info("[TRACKING] wamidOut={} wamidIn={} type={}",
                    event.wamidOutbound(), event.wamidInbound(), event.trackingType());

                Optional<CorrelationDocument> correlationOpt = correlationRepo.findByWamid(event.wamidOutbound());
                CorrelationDocument doc = correlationOpt.orElse(null);

                Map<String, Object> xdm = xdmMapper.buildTrackingXdm(
                    doc, event.wamidOutbound(), event.wamidInbound(),
                    event.metaTimestamp(), event.trackingType(),
                    event.reply(), event.buttonId(), event.buttonTitle()
                );
                cdpPublisher.publish(xdm);

                onProcessed.run();
            });
    }

    // ── Mapeadores puros ───────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private DeliveryEvent toDeliveryEvent(Map<String, Object> status) {
        String wamid  = getString(status, "id");
        String stat   = getString(status, "status");
        String metaTs = getString(status, "timestamp");

        Map<String, Object> errObj = getMap(status, "errors");
        String errCode = errObj != null ? getString(errObj, "code") : null;
        String errMsg  = errObj != null ? getString(errObj, "title") : null;

        return new DeliveryEvent(wamid, stat, metaTs, errCode, errMsg);
    }

    @SuppressWarnings("unchecked")
    private TrackingEvent toTrackingEvent(Map<String, Object> message) {
        String type     = getString(message, "type");
        String wamidIn  = getString(message, "id");
        String metaTs   = getString(message, "timestamp");

        Map<String, Object> ctx = getMap(message, "context");
        String wamidOut = ctx != null ? getString(ctx, "id") : wamidIn;

        if ("interactive".equals(type)) {
            Map<String, Object> interactive = getMap(message, "interactive");
            if (interactive != null) {
                String subType = getString(interactive, "type");
                if ("button_reply".equals(subType)) {
                    Map<String, Object> btn = getMap(interactive, "button_reply");
                    return new TrackingEvent(
                        wamidOut, wamidIn, metaTs, "button_reply",
                        btn != null ? getString(btn, "title") : "",
                        btn != null ? getString(btn, "id") : "",
                        btn != null ? getString(btn, "title") : ""
                    );
                }
            }
        } else if ("text".equals(type)) {
            Map<String, Object> textObj = getMap(message, "text");
            String text = textObj != null ? getString(textObj, "body") : "";
            return new TrackingEvent(wamidOut, wamidIn, metaTs, "text_reply", text, null, null);
        }

        return null;
    }

    // ── Extracción segura y funcional de colecciones JSON ───────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractValues(Map<String, Object> payload) {
        return Optional.ofNullable(payload)
            .map(p -> (List<Map<String, Object>>) p.get("entry"))
            .orElse(List.of())
            .stream()
            .map(e -> (List<Map<String, Object>>) e.get("changes"))
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(c -> (Map<String, Object>) c.get("value"))
            .filter(Objects::nonNull)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> map, String key) {
        return Optional.ofNullable((List<Map<String, Object>>) map.get(key)).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}

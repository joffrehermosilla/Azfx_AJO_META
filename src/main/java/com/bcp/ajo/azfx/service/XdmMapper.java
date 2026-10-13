package com.bcp.ajo.azfx.service;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * XdmMapper — Construye el XDM ExperienceEvent para Adobe CDP
 *
 * Contrato C: XDM custom BCP basado en el schema:
 *   eventType: dinámico según taxonomía BCP
 *   _bcp.messaging.whatsapp.*
 *
 * Taxonomía de eventType:
 *   bcp.whatsapp.delivery.sent
 *   bcp.whatsapp.delivery.delivered
 *   bcp.whatsapp.delivery.read
 *   bcp.whatsapp.delivery.failed
 *   bcp.whatsapp.tracking.button_reply
 *   bcp.whatsapp.tracking.text_reply
 *   bcp.whatsapp.consent.opt_in
 *   bcp.whatsapp.consent.opt_out
 */
public class XdmMapper {

    private static final Logger log = LoggerFactory.getLogger(XdmMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Construye el XDM para un evento de DELIVERY (sent/delivered/read/failed).
     */
    public Map<String, Object> buildDeliveryXdm(
        CorrelationDocument correlation,
        String status,
        String wamid,
        String metaTimestamp,
        String errorCode,
        String errorMsg
    ) {
        String now       = Instant.now().toString();
        String eventType = resolveDeliveryEventType(status);

        Map<String, Object> xdm = new LinkedHashMap<>();
        xdm.put("_id", UUID.randomUUID().toString());
        xdm.put("timestamp", now);
        xdm.put("eventType", eventType); // <-- Corregido y aplicado correctamente

        // _bcp root
        Map<String, Object> bcp = new LinkedHashMap<>();

        // _bcp.event
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventType);
        bcp.put("event", event);

        // _bcp.identity
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("customerId", correlation != null ? correlation.getCustomerId() : null);
        bcp.put("identity", identity);

        // _bcp.messaging.whatsapp
        Map<String, Object> whatsapp = buildWhatsappContext(correlation, wamid, metaTimestamp, now);

        // _bcp.messaging.whatsapp.delivery
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("status", status);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code",    errorCode);
        error.put("message", errorMsg);
        delivery.put("error", error);
        whatsapp.put("delivery", delivery);

        Map<String, Object> messaging = new LinkedHashMap<>();
        messaging.put("whatsapp", whatsapp);
        bcp.put("messaging", messaging);

        xdm.put("_bcp", bcp);

        log.info("[XDM] Delivery event construido — eventType={} wamid={} customerId={}",
            eventType, wamid, correlation != null ? correlation.getCustomerId() : "UNCORRELATED");
        return xdm;
    }

    /**
     * Construye el XDM para un evento de TRACKING (button_reply / text_reply).
     */
    public Map<String, Object> buildTrackingXdm(
        CorrelationDocument correlation,
        String wamidOutbound,
        String wamidInbound,
        String metaTimestamp,
        String trackingType,
        String reply,
        String buttonId,
        String buttonTitle
    ) {
        String now       = Instant.now().toString();
        String eventType = "button_reply".equals(trackingType)
            ? "bcp.whatsapp.tracking.button_reply"
            : "bcp.whatsapp.tracking.text_reply";

        Map<String, Object> xdm = new LinkedHashMap<>();
        xdm.put("_id", UUID.randomUUID().toString());
        xdm.put("timestamp", now);
        xdm.put("eventType", eventType); // <-- Corregido y aplicado correctamente

        Map<String, Object> bcp = new LinkedHashMap<>();

        // _bcp.event
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventType);
        bcp.put("event", event);

        // _bcp.identity
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("customerId", correlation != null ? correlation.getCustomerId() : null);
        bcp.put("identity", identity);

        // _bcp.messaging.whatsapp
        Map<String, Object> whatsapp = buildWhatsappContext(correlation, wamidOutbound, metaTimestamp, now);

        // _bcp.messaging.whatsapp.message — agrega wamid inbound
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) whatsapp.get("message");
        message.put("messageId", wamidInbound);

        // _bcp.messaging.whatsapp.tracking
        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("type",        trackingType);
        tracking.put("reply",       reply);
        tracking.put("buttonId",    buttonId);
        tracking.put("buttonTitle", buttonTitle);
        whatsapp.put("tracking", tracking);

        Map<String, Object> messaging = new LinkedHashMap<>();
        messaging.put("whatsapp", whatsapp);
        bcp.put("messaging", messaging);

        xdm.put("_bcp", bcp);

        log.info("[XDM] Tracking event construido — eventType={} wamidOut={} wamidIn={}",
            eventType, wamidOutbound, wamidInbound);
        return xdm;
    }

    // ── Helper: construye el contexto whatsapp compartido ──────────────────
    private Map<String, Object> buildWhatsappContext(
        CorrelationDocument c,
        String wamid,
        String metaTimestamp,
        String now
    ) {
        Map<String, Object> whatsapp = new LinkedHashMap<>();

        // audit
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("metaTimestamp",     metaTimestamp);
        audit.put("receivedTimestamp", now);
        audit.put("source",            "meta_webhook");
        whatsapp.put("audit", audit);

        // correlation
        Map<String, Object> corr = new LinkedHashMap<>();
        corr.put("correlationId",     c != null ? c.getCorrelationId()  : null);
        corr.put("correlationStatus", c != null ? "CORRELATED" : "UNCORRELATED");
        corr.put("executionType",     c != null ? c.getExecutionType()  : "UNKNOWN");
        whatsapp.put("correlation", corr);

        // journeyContext
        Map<String, Object> jCtx = new LinkedHashMap<>();
        jCtx.put("journeyId",          c != null ? c.getJourneyId()        : null);
        jCtx.put("journeyVersionId",   c != null ? c.getJourneyVersionId() : null);
        jCtx.put("journeyVersionName", c != null ? c.getJourneyVersionName(): null);
        jCtx.put("journeyNodeId",      c != null ? c.getJourneyNodeId()    : null);
        jCtx.put("journeyActionId",    c != null ? c.getJourneyActionId()  : null);
        whatsapp.put("journeyContext", jCtx);

        // message
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("campaignId",    c != null ? c.getCampaignId()    : null);
        message.put("messageId",     null); 
        message.put("templateName",  c != null ? c.getTemplateName()  : null);
        message.put("templateWamId", c != null ? c.getTemplateName()  : null);
        message.put("wamId",         wamid);
        whatsapp.put("message", message);

        // messageProfile
        Map<String, Object> messageProfile = new LinkedHashMap<>();
        messageProfile.put("channel",          "whatsapp");
        messageProfile.put("isTestExecution",  false);
        messageProfile.put("messageProfileId", c != null ? c.getCorrelationId() : null);
        whatsapp.put("messageProfile", messageProfile);

        return whatsapp;
    }

    // ── Resolución de eventType para delivery ──────────────────────────────
    private String resolveDeliveryEventType(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "sent"      -> "bcp.whatsapp.delivery.sent";
            case "delivered" -> "bcp.whatsapp.delivery.delivered";
            case "read"      -> "bcp.whatsapp.delivery.read";
            case "failed",
                 "error"     -> "bcp.whatsapp.delivery.failed";
            default          -> "bcp.whatsapp.delivery." + status;
        };
    }
}
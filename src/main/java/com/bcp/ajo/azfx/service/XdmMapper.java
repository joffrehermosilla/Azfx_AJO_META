package com.bcp.ajo.azfx.service;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * XdmMapper: construye el evento XDM custom BCP para el inlet de Adobe CDP.
 * eventType es fijo ("whatsapp.log"); la taxonomía va en _bcp.event.name:
 * bcp.whatsapp.delivery.{sent|delivered|read|failed}
 * bcp.whatsapp.tracking.{button_reply|text_reply}
 */
public class XdmMapper {

    private static final Logger log = LoggerFactory.getLogger(XdmMapper.class);
    public static final String CDP_EVENT_TYPE = "whatsapp.log";

    public Map<String, Object> buildDeliveryXdm(
            CorrelationDocument correlation, String status, String wamid,
            String metaTimestamp, String errorCode, String errorMsg) {

        String eventName = resolveDeliveryEventName(status);
        Map<String, Object> whatsapp = buildWhatsappContext(correlation, wamid, metaTimestamp);

        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("status", status);
        if (!isBlank(errorCode) || !isBlank(errorMsg)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", errorCode);
            error.put("message", errorMsg);
            delivery.put("error", error);
        }
        whatsapp.put("delivery", delivery);

        log.info("[XDM] Delivery construido, eventName={} wamid={} correlated={}",
                eventName, wamid, correlation != null);
        return envelope(eventName, customerIdOf(correlation, null), whatsapp);
    }

    /** Compatibilidad con llamadas de 8 argumentos. */
    public Map<String, Object> buildTrackingXdm(
            CorrelationDocument correlation, String wamidOutbound, String wamidInbound,
            String metaTimestamp, String trackingType, String reply,
            String buttonId, String buttonTitle) {
        return buildTrackingXdm(correlation, wamidOutbound, wamidInbound, metaTimestamp,
                trackingType, reply, buttonId, buttonTitle, null, null);
    }

    /** Versión completa: soporta Quick Reply con payload "cid=...|tn=...". */
    public Map<String, Object> buildTrackingXdm(
            CorrelationDocument correlation, String wamidOutbound, String wamidInbound,
            String metaTimestamp, String trackingType, String reply,
            String buttonId, String buttonTitle,
            String payloadCustomerId, String payloadTemplateName) {

        String eventName = "button_reply".equals(trackingType)
                ? "bcp.whatsapp.tracking.button_reply"
                : "bcp.whatsapp.tracking.text_reply";

        Map<String, Object> whatsapp = buildWhatsappContext(correlation, wamidOutbound, metaTimestamp);

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) whatsapp.get("message");
        message.put("messageId", wamidInbound);
        if (isBlank((String) message.get("templateName")) && !isBlank(payloadTemplateName)) {
            message.put("templateName", payloadTemplateName);
        }

        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("type", trackingType);
        tracking.put("reply", reply);
        tracking.put("buttonId", buttonId);
        tracking.put("buttonTitle", buttonTitle);
        whatsapp.put("tracking", tracking);

        log.info("[XDM] Tracking construido, eventName={} wamidOut={} wamidIn={}",
                eventName, wamidOutbound, wamidInbound);
        return envelope(eventName, customerIdOf(correlation, payloadCustomerId), whatsapp);
    }

    // Envelope común

    private Map<String, Object> envelope(String eventName, String customerId, Map<String, Object> whatsapp) {
        Map<String, Object> xdm = new LinkedHashMap<>();
        xdm.put("_id", UUID.randomUUID().toString());
        xdm.put("timestamp", Instant.now().toString());
        xdm.put("eventType", CDP_EVENT_TYPE);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", eventName);

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("customerId", customerId);

        Map<String, Object> messaging = new LinkedHashMap<>();
        messaging.put("whatsapp", whatsapp);

        Map<String, Object> bcp = new LinkedHashMap<>();
        bcp.put("event", event);
        bcp.put("identity", identity);
        bcp.put("messaging", messaging);

        xdm.put("_bcp", bcp);
        return xdm;
    }

    private Map<String, Object> buildWhatsappContext(CorrelationDocument c, String wamid, String metaTimestamp) {
        Map<String, Object> whatsapp = new LinkedHashMap<>();

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("metaTimestamp", toIso(metaTimestamp));
        audit.put("receivedTimestamp", Instant.now().toString());
        audit.put("source", "meta_webhook");
        whatsapp.put("audit", audit);

        Map<String, Object> corr = new LinkedHashMap<>();
        corr.put("correlationId", c != null ? c.getCorrelationId() : null);
        corr.put("correlationStatus", c != null ? "CORRELATED" : "UNCORRELATED");
        corr.put("executionType", c != null ? c.getExecutionType() : "UNKNOWN");
        whatsapp.put("correlation", corr);

        Map<String, Object> jCtx = new LinkedHashMap<>();
        jCtx.put("journeyId", c != null ? c.getJourneyId() : null);
        jCtx.put("journeyVersionId", c != null ? c.getJourneyVersionId() : null);
        jCtx.put("journeyVersionName", c != null ? c.getJourneyVersionName() : null);
        jCtx.put("journeyNodeId", c != null ? c.getJourneyNodeId() : null);
        jCtx.put("journeyActionId", c != null ? c.getJourneyActionId() : null);
        whatsapp.put("journeyContext", jCtx);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("campaignId", c != null ? c.getCampaignId() : null);
        message.put("messageId", null);
        message.put("templateName", c != null ? c.getTemplateName() : null);
        message.put("templateWamId", c != null ? c.getTemplateWamId() : null);
        message.put("wamId", wamid);
        whatsapp.put("message", message);

        Map<String, Object> messageProfile = new LinkedHashMap<>();
        messageProfile.put("channel", "whatsapp");
        messageProfile.put("isTestExecution", c != null && Boolean.TRUE.equals(c.getIsTestExecution()));
        messageProfile.put("messageProfileId", c != null ? c.getMessageProfileId() : null);
        whatsapp.put("messageProfile", messageProfile);

        return whatsapp;
    }

    // Helpers

    private static String customerIdOf(CorrelationDocument c, String fallback) {
        if (c != null && !isBlank(c.getCustomerId()))
            return c.getCustomerId();
        return isBlank(fallback) ? null : fallback;
    }

    /** Meta envía epoch en segundos; el schema XDM espera date-time ISO-8601. */
    static String toIso(String metaTimestamp) {
        if (isBlank(metaTimestamp))
            return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(metaTimestamp.trim())).toString();
        } catch (NumberFormatException e) {
            return metaTimestamp; // ya viene en otro formato; no se altera
        }
    }

    private String resolveDeliveryEventName(String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "sent" -> "bcp.whatsapp.delivery.sent";
            case "delivered" -> "bcp.whatsapp.delivery.delivered";
            case "read" -> "bcp.whatsapp.delivery.read";
            case "failed", "error" -> "bcp.whatsapp.delivery.failed";
            default -> "bcp.whatsapp.delivery." + (s.isEmpty() ? "unknown" : s);
        };
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
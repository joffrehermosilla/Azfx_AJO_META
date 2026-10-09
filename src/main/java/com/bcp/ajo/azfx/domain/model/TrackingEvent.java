package com.bcp.ajo.azfx.domain.model;

/**
 * TrackingEvent — Record inmutable que representa una interacción del usuario.
 * (button_reply, text_reply, flow response).
 */
public record TrackingEvent(
    String wamidOutbound,
    String wamidInbound,
    String metaTimestamp,
    String trackingType,
    String reply,
    String buttonId,
    String buttonTitle,
    String payloadCustomerId,
    String payloadTemplateName
) {
    // Constructor de conveniencia (compatibilidad con llamadas anteriores de 7 argumentos)
    public TrackingEvent(
        String wamidOutbound,
        String wamidInbound,
        String metaTimestamp,
        String trackingType,
        String reply,
        String buttonId,
        String buttonTitle
    ) {
        this(wamidOutbound, wamidInbound, metaTimestamp, trackingType, reply, buttonId, buttonTitle, null, null);
    }
}
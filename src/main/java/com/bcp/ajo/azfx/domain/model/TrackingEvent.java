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
    String buttonTitle
) {}

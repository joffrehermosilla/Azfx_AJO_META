package com.bcp.ajo.azfx.domain.model;

/**
 * DeliveryEvent — Record inmutable que representa un evento de entrega recibido de Meta.
 * Estados típicos: sent, delivered, read, failed.
 */
public record DeliveryEvent(
    String wamid,
    String status,
    String metaTimestamp,
    String errorCode,
    String errorMessage
) {
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status);
    }
}

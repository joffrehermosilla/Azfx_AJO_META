package com.bcp.ajo.azfx.domain.model;

/**
 * AjoContextCommand — Inmutable Command para registrar el contexto de una campaña AJO.
 * Representa los datos provenientes de la Custom Action 2 del Journey.
 */
public record AjoContextCommand(
    String wamid,
    String customerId,
    String recipient,
    String waId,
    String messageStatus,
    String namespace,
    String journeyId,
    String journeyVersionId,
    String journeyVersionName,
    String journeyInstanceId,
    String journeyNodeId,
    String journeyNodeName,
    String journeyActionId,
    String journeyActionName,
    String templateName,
    String templateCategory,
    String sandboxName,
    String executionType
) {
    public AjoContextCommand {
        if (wamid == null || wamid.isBlank()) {
            throw new IllegalArgumentException("El campo 'wamid' es obligatorio");
        }
        if (journeyId == null || journeyId.isBlank()) {
            throw new IllegalArgumentException("El campo 'journeyId' es obligatorio");
        }
    }
}

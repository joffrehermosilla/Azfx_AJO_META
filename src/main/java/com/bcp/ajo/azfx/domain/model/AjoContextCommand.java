package com.bcp.ajo.azfx.domain.model;

/**
 * Immutable command created from AJO Custom Action 2 and persisted for
 * correlation.
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
        String executionType,
        String campaignId,
        String templateWamId,
        String messageProfileId,
        Boolean isTestExecution) {
    /**
     * Backwards-compatible constructor for existing callers/tests using the old
     * 18-field contract.
     */
    public AjoContextCommand(
            String wamid, String customerId, String recipient, String waId, String messageStatus,
            String namespace, String journeyId, String journeyVersionId, String journeyVersionName,
            String journeyInstanceId, String journeyNodeId, String journeyNodeName,
            String journeyActionId, String journeyActionName, String templateName,
            String templateCategory, String sandboxName, String executionType) {
        this(wamid, customerId, recipient, waId, messageStatus, namespace, journeyId,
                journeyVersionId, journeyVersionName, journeyInstanceId, journeyNodeId,
                journeyNodeName, journeyActionId, journeyActionName, templateName,
                templateCategory, sandboxName, executionType, null, null, null, false);
    }

    public AjoContextCommand {
        if (wamid == null || wamid.isBlank()) {
            throw new IllegalArgumentException("El campo 'wamid' es obligatorio");
        }
        if (journeyId == null || journeyId.isBlank()) {
            throw new IllegalArgumentException("El campo 'journeyId' es obligatorio");
        }
        if (isTestExecution == null)
            isTestExecution = false;
    }
}

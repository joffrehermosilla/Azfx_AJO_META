package com.bcp.ajo.azfx.domain.model;

/**
 * ProcessingResult — Resultado funcional inmutable del procesamiento de webhooks.
 */
public record ProcessingResult(
    boolean success,
    int itemsProcessed,
    String message
) {
    public static ProcessingResult ok(int items) {
        return new ProcessingResult(true, items, "Processed successfully");
    }

    public static ProcessingResult unauthorized(String msg) {
        return new ProcessingResult(false, 0, msg);
    }

    public static ProcessingResult error(String msg) {
        return new ProcessingResult(false, 0, msg);
    }
}

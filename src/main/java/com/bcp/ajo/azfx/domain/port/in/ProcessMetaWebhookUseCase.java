package com.bcp.ajo.azfx.domain.port.in;

import com.bcp.ajo.azfx.domain.model.ProcessingResult;

/**
 * Puerto de Entrada (Driving Port) para el procesamiento de callbacks de Meta.
 */
@FunctionalInterface
public interface ProcessMetaWebhookUseCase {
    ProcessingResult execute(String rawBody, String signatureHeader);
}

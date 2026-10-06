package com.bcp.ajo.azfx.domain.port.in;

import com.bcp.ajo.azfx.domain.model.WebhookChallenge;
import java.util.Optional;

/**
 * Puerto de Entrada (Driving Port) para el handshake de verificación de Meta.
 */
@FunctionalInterface
public interface VerifyMetaWebhookUseCase {
    Optional<String> execute(WebhookChallenge challenge);
}

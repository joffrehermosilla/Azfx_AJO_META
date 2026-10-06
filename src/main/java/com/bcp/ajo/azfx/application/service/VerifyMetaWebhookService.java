package com.bcp.ajo.azfx.application.service;

import com.bcp.ajo.azfx.domain.model.WebhookChallenge;
import com.bcp.ajo.azfx.domain.port.in.VerifyMetaWebhookUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * VerifyMetaWebhookService — Caso de uso funcional para la verificación de webhooks de Meta.
 */
public class VerifyMetaWebhookService implements VerifyMetaWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerifyMetaWebhookService.class);
    private final String expectedToken;

    public VerifyMetaWebhookService(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public Optional<String> execute(WebhookChallenge challenge) {
        return Optional.ofNullable(challenge)
            .filter(c -> "subscribe".equals(c.mode()))
            .filter(c -> expectedToken != null && expectedToken.equals(c.token()))
            .map(WebhookChallenge::challenge)
            .map(res -> {
                log.info("[USE-CASE] Handshake de Meta verificado con éxito");
                return res;
            });
    }
}

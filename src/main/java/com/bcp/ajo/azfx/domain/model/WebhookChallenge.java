package com.bcp.ajo.azfx.domain.model;

/**
 * WebhookChallenge — Record inmutable para el handshake de verificación GET de Meta.
 */
public record WebhookChallenge(
    String mode,
    String token,
    String challenge
) {}

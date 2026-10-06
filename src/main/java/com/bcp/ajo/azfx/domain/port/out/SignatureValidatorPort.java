package com.bcp.ajo.azfx.domain.port.out;

/**
 * Puerto de Salida (Driven Port) para la validación criptográfica de firmas HMAC-SHA256.
 */
@FunctionalInterface
public interface SignatureValidatorPort {
    boolean isValid(String body, String signatureHeader);
}

package com.bcp.ajo.azfx.util;

import com.bcp.ajo.azfx.domain.port.out.SignatureValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * MetaSignatureValidator — Adaptador de seguridad (Driven Adapter) para validar HMAC-SHA256 de Meta.
 * Implementa el puerto SignatureValidatorPort.
 */
public class MetaSignatureValidator implements SignatureValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(MetaSignatureValidator.class);

    private final String appSecret;

    public MetaSignatureValidator() {
        this(System.getenv("META_APP_SECRET"));
    }

    public MetaSignatureValidator(String appSecret) {
        this.appSecret = appSecret;
    }

    /**
     * Valida la firma HMAC-SHA256 del body del request.
     *
     * @param body             Body raw del request HTTP como String.
     * @param signatureHeader  Valor del header X-Hub-Signature-256 (ej: "sha256=abc123...").
     * @return true si la firma es válida.
     */
    public boolean isValid(String body, String signatureHeader) {
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("[SIGNATURE] META_APP_SECRET no configurado — saltando validación (solo DEV).");
            return true; // En DEV se permite sin firma. En PROD configurar el secret.
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("[SIGNATURE] Header X-Hub-Signature-256 ausente o malformado.");
            return false;
        }

        try {
            String expected = signatureHeader.substring("sha256=".length()).toLowerCase();

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

            String computed = HexFormat.of().formatHex(hash).toLowerCase();

            boolean valid = computed.equals(expected);
            if (!valid) {
                log.warn("[SIGNATURE] Firma inválida. expected={} computed={}", expected, computed);
            }
            return valid;

        } catch (Exception e) {
            log.error("[SIGNATURE] Error validando firma: {}", e.getMessage(), e);
            return false;
        }
    }
}

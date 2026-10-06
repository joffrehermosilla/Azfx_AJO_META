package com.bcp.ajo.azfx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class MetaSignatureValidatorTest {

    private static final String APP_SECRET = "test_meta_app_secret_123456";

    private String calculateExpectedSignature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash).toLowerCase();
    }

    @Test
    @DisplayName("isValid retorna true con firma HMAC-SHA256 válida")
    void shouldReturnTrueWhenSignatureIsValid() throws Exception {
        MetaSignatureValidator validator = new MetaSignatureValidator(APP_SECRET);
        String body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";
        String validHeader = calculateExpectedSignature(body, APP_SECRET);

        boolean result = validator.isValid(body, validHeader);
        assertTrue(result, "La firma calculada debió ser validada como correcta");
    }

    @Test
    @DisplayName("isValid retorna false con firma adulterada o errónea")
    void shouldReturnFalseWhenSignatureIsInvalid() {
        MetaSignatureValidator validator = new MetaSignatureValidator(APP_SECRET);
        String body = "{\"object\":\"whatsapp_business_account\"}";
        String invalidHeader = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        boolean result = validator.isValid(body, invalidHeader);
        assertFalse(result, "La firma errónea debió ser rechazada");
    }

    @Test
    @DisplayName("isValid retorna false si el header no contiene el prefijo sha256=")
    void shouldReturnFalseWhenHeaderIsMalformed() {
        MetaSignatureValidator validator = new MetaSignatureValidator(APP_SECRET);
        String body = "{\"entry\":[]}";

        assertFalse(validator.isValid(body, null));
        assertFalse(validator.isValid(body, ""));
        assertFalse(validator.isValid(body, "md5=123456"));
    }

    @Test
    @DisplayName("isValid retorna true en DEV si appSecret es nulo o vacío")
    void shouldReturnTrueInDevWhenAppSecretNotConfigured() {
        MetaSignatureValidator validator = new MetaSignatureValidator(null);
        String body = "{\"test\":true}";

        assertTrue(validator.isValid(body, "sha256=cualquiera"));
    }
}

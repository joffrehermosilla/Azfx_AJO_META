package com.bcp.ajo.azfx.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PhoneDacUtils — Utilidad BCP para la protección de Datos de Acceso Controlado
 * (DAC).
 * Ofusca para logs y genera hashes SHA-256 estables para
 * almacenamiento/correlación.
 */
public class PhoneDacUtils {

    /**
     * Ofusca el número de teléfono para logs de Application Insights.
     * Ejemplo: "51999888777" -> "519****8777"
     */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return "UNKNOWN";
        }
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.length() <= 6) {
            return "***";
        }
        int startLen = 3;
        int endLen = 4;
        return clean.substring(0, startLen) + "****" + clean.substring(clean.length() - endLen);
    }

    /**
     * Genera un Hash SHA-256 determinista del teléfono para almacenamiento o
     * transmisión enmascarada.
     */
    public static String hashSha256(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error calculando SHA-256 para teléfono DAC", e);
        }
    }
}
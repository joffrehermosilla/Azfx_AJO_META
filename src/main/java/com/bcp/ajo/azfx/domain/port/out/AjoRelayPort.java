package com.bcp.ajo.azfx.domain.port.out;

import java.util.Map;

/**
 * Puerto de Salida (Driven Port) para el reenvío de eventos nativos hacia AJO Webhook Ingest API.
 */
@FunctionalInterface
public interface AjoRelayPort {
    boolean relay(Map<String, Object> metaPayload);
}

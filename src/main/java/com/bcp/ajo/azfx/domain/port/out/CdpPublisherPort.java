package com.bcp.ajo.azfx.domain.port.out;

import java.util.Map;

/**
 * Puerto de Salida (Driven Port) para la publicación de eventos XDM a Adobe CDP.
 */
@FunctionalInterface
public interface CdpPublisherPort {
    boolean publish(Map<String, Object> xdmEvent);
}

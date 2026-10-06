package com.bcp.ajo.azfx.domain.port.out;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import java.util.Optional;

/**
 * Puerto de Salida (Driven Port) para la persistencia operacional de correlación (Cosmos DB).
 */
public interface CorrelationRepositoryPort {
    void upsert(CorrelationDocument doc);
    Optional<CorrelationDocument> findByWamid(String wamid);
    void updateStatus(String wamid, String eventType, String metaTimestamp, String now);
}

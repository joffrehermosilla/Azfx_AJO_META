package com.bcp.ajo.azfx.domain.port.in;

import com.bcp.ajo.azfx.domain.model.AjoContextCommand;
import com.bcp.ajo.azfx.model.CorrelationDocument;

/**
 * Puerto de Entrada (Driving Port) para el caso de uso de almacenamiento de contexto AJO.
 */
@FunctionalInterface
public interface StoreAjoContextUseCase {
    CorrelationDocument execute(AjoContextCommand command);
}

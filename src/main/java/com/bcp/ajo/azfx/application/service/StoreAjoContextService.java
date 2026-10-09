package com.bcp.ajo.azfx.application.service;

import com.bcp.ajo.azfx.domain.model.AjoContextCommand;
import com.bcp.ajo.azfx.domain.port.in.StoreAjoContextUseCase;
import com.bcp.ajo.azfx.domain.port.out.CorrelationRepositoryPort;
import com.bcp.ajo.azfx.model.CorrelationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bcp.ajo.azfx.util.PhoneDacUtils;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * StoreAjoContextService — Implementación del caso de uso de almacenamiento de
 * contexto AJO.
 * Aplica programación funcional para mapear el comando a la entidad de
 * persistencia.
 */
public class StoreAjoContextService implements StoreAjoContextUseCase {

    private static final Logger log = LoggerFactory.getLogger(StoreAjoContextService.class);
    private final CorrelationRepositoryPort repository;

    // Función pura de transformación de Command a Document
    private static final Function<AjoContextCommand, CorrelationDocument> TO_DOCUMENT = cmd -> {
        String now = Instant.now().toString();
        CorrelationDocument doc = new CorrelationDocument();
        doc.setId(cmd.wamid());
        doc.setWamid(cmd.wamid());
        doc.setCustomerId(cmd.customerId());
        // En StoreAjoContextService.java al mapear AjoContextCommand
        doc.setRecipient(PhoneDacUtils.hashSha256(cmd.recipient()));
        // Se guarda encriptado SHA-256
        doc.setWaId(cmd.waId() != null ? cmd.waId() : cmd.recipient());
        doc.setNamespace(cmd.namespace());
        doc.setJourneyId(cmd.journeyId());
        doc.setJourneyVersionId(cmd.journeyVersionId());
        doc.setJourneyVersionName(cmd.journeyVersionName());
        doc.setJourneyInstanceId(cmd.journeyInstanceId());
        doc.setJourneyNodeId(cmd.journeyNodeId());
        doc.setJourneyNodeName(cmd.journeyNodeName());
        doc.setJourneyActionId(cmd.journeyActionId());
        doc.setJourneyActionName(cmd.journeyActionName());
        doc.setTemplateName(cmd.templateName());
        doc.setTemplateCategory(cmd.templateCategory());
        doc.setSandboxName(cmd.sandboxName());
        doc.setExecutionType(cmd.executionType() != null ? cmd.executionType() : "CUSTOM");
        doc.setCorrelationId(UUID.randomUUID().toString());
        doc.setCorrelationStatus("STORED");
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return doc;
    };

    public StoreAjoContextService(CorrelationRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public CorrelationDocument execute(AjoContextCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        CorrelationDocument doc = TO_DOCUMENT.apply(command);
        repository.upsert(doc);

        log.info("[USE-CASE] Contexto AJO guardado exitosamente — wamid={} correlationId={}",
                doc.getWamid(), doc.getCorrelationId());
        return doc;
    }
}

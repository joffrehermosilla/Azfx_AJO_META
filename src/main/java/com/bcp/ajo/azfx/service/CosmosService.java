package com.bcp.ajo.azfx.service;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.bcp.ajo.azfx.domain.port.out.CorrelationRepositoryPort;
import com.bcp.ajo.azfx.model.CorrelationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * CosmosService — Adaptador de salida (Driven Adapter) para Cosmos DB NoSQL.
 * Implementa el puerto CorrelationRepositoryPort.
 */
public class CosmosService implements CorrelationRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(CosmosService.class);

    // ── Singleton cacheado para evitar reconexiones entre invocaciones ──────
    private static volatile CosmosClient client;
    private static volatile CosmosContainer correlationContainer;
    private static volatile CosmosContainer eventsContainer;

    private final String endpoint;
    private final String key;
    private final String database;
    private final String correlationContainerName;
    private final String eventsContainerName;

    public CosmosService() {
        this.endpoint               = System.getenv("COSMOS_ENDPOINT");
        this.key                    = System.getenv("COSMOS_KEY");
        this.database               = System.getenv("COSMOS_DATABASE");
        this.correlationContainerName = System.getenv("COSMOS_CONTAINER_CORRELATION");
        this.eventsContainerName    = System.getenv("COSMOS_CONTAINER_EVENTS");
    }

    // ───────────────────────────────────────────────────────────────────────
    // Conexión cacheada (double-checked locking)
    // ───────────────────────────────────────────────────────────────────────

    private CosmosContainer getCorrelationContainer() {
        if (correlationContainer == null) {
            synchronized (CosmosService.class) {
                if (correlationContainer == null) {
                    log.info("[COSMOS] Inicializando cliente Cosmos: {}", endpoint);
                    client = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .buildClient();
                    CosmosDatabase db = client.getDatabase(database);
                    correlationContainer = db.getContainer(correlationContainerName);
                    eventsContainer      = db.getContainer(eventsContainerName);
                    log.info("[COSMOS] Conexión establecida. DB={} Container={}", database, correlationContainerName);
                }
            }
        }
        return correlationContainer;
    }

    private CosmosContainer getEventsContainer() {
        getCorrelationContainer(); // asegura inicialización
        return eventsContainer;
    }

    // ───────────────────────────────────────────────────────────────────────
    // OPERACIONES
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Guarda o actualiza el documento de correlación en Cosmos.
     * Upsert garantiza idempotencia si CA2 se reintenta.
     *
     * @param doc Documento de correlación construido por Function 1.
     */
    public void upsertCorrelation(CorrelationDocument doc) {
        try {
            CosmosItemResponse<CorrelationDocument> response =
                getCorrelationContainer().upsertItem(
                    doc,
                    new PartitionKey(doc.getWamid()),
                    new CosmosItemRequestOptions()
                );
            log.info("[COSMOS] Upsert OK — wamid={} status={} RU={}",
                doc.getWamid(),
                response.getStatusCode(),
                response.getRequestCharge());
        } catch (Exception e) {
            log.error("[COSMOS] Error en upsert — wamid={} error={}", doc.getWamid(), e.getMessage(), e);
            throw new RuntimeException("Cosmos upsert failed: " + e.getMessage(), e);
        }
    }

    /**
     * Busca el documento de correlación por WAMID.
     * El WAMID es a la vez el "id" del documento y la partitionKey.
     *
     * @param wamid WAMID del mensaje outbound de Meta.
     * @return CorrelationDocument o null si no existe.
     */
    @Override
    public void upsert(CorrelationDocument doc) {
        upsertCorrelation(doc);
    }

    /**
     * Busca el documento de correlación por WAMID.
     * El WAMID es a la vez el "id" del documento y la partitionKey.
     *
     * @param wamid WAMID del mensaje outbound de Meta.
     * @return Optional con el CorrelationDocument o vacío si no existe.
     */
    @Override
    public Optional<CorrelationDocument> findByWamid(String wamid) {
        return Optional.ofNullable(findDocument(wamid));
    }

    public CorrelationDocument findDocument(String wamid) {
        try {
            CosmosItemResponse<CorrelationDocument> response =
                getCorrelationContainer().readItem(
                    wamid,
                    new PartitionKey(wamid),
                    CorrelationDocument.class
                );
            log.info("[COSMOS] Lookup OK — wamid={} customerId={}",
                wamid, response.getItem().getCustomerId());
            return response.getItem();
        } catch (com.azure.cosmos.CosmosException e) {
            if (e.getStatusCode() == 404) {
                log.warn("[COSMOS] WAMID no encontrado — wamid={}", wamid);
                return null;
            }
            log.error("[COSMOS] Error en lookup — wamid={} error={}", wamid, e.getMessage(), e);
            throw new RuntimeException("Cosmos lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateStatus(String wamid, String eventType, String metaTimestamp, String now) {
        updateEventStatus(wamid, eventType, metaTimestamp, now);
    }

    /**
     * Actualiza el estado del documento tras recibir un callback de Meta.
     * Actualiza: correlationStatus, lastEventType, lastEventAt, lastMetaTimestamp, updatedAt.
     *
     * @param wamid         WAMID del documento a actualizar.
     * @param eventType     Tipo de evento BCP (ej: "bcp.whatsapp.delivery.delivered").
     * @param metaTimestamp Timestamp de Meta del callback.
     * @param now           Timestamp actual ISO-8601.
     */
    public void updateEventStatus(String wamid, String eventType, String metaTimestamp, String now) {
        try {
            CorrelationDocument existing = findDocument(wamid);
            if (existing == null) {
                log.warn("[COSMOS] No se puede actualizar — documento no existe para wamid={}", wamid);
                return;
            }
            existing.setCorrelationStatus("CORRELATED");
            existing.setLastEventType(eventType);
            existing.setLastEventAt(now);
            existing.setLastMetaTimestamp(metaTimestamp);
            existing.setUpdatedAt(now);

            getCorrelationContainer().upsertItem(
                existing,
                new PartitionKey(wamid),
                new CosmosItemRequestOptions()
            );
            log.info("[COSMOS] Estado actualizado — wamid={} eventType={}", wamid, eventType);
        } catch (Exception e) {
            // No propagamos — la actualización de estado no debe bloquear el envío a CDP
            log.warn("[COSMOS] Error actualizando estado (no bloqueante) — wamid={} error={}", wamid, e.getMessage());
        }
    }
}

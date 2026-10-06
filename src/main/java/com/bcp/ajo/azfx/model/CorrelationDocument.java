package com.bcp.ajo.azfx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CorrelationDocument — Contrato B: Documento almacenado en Cosmos DB
 *
 * Representa el estado operacional de correlación de un mensaje WhatsApp.
 * Se indexa por WAMID (partitionKey = /wamid).
 *
 * TTL: 604800 segundos (7 días). Cosmos elimina el documento automáticamente.
 * El histórico permanente está en CDP/AEP — Cosmos es solo el store operacional.
 *
 * Referencia BCP: "Tabla de correlación: WAMID → customerId, journeyId, correlationId"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CorrelationDocument {

    /**
     * id en Cosmos = wamid. Sirve como clave única del documento.
     * Cosmos requiere campo "id" explícito.
     */
    @JsonProperty("id")
    private String id;

    /** WAMID generado por Meta para el mensaje outbound. Clave de correlación. */
    @JsonProperty("wamid")
    private String wamid;

    /** ID del cliente BCP (CICID). Primary identity para CDP. */
    @JsonProperty("customerId")
    private String customerId;

    /** Número E.164 del destinatario (sin '+'). */
    @JsonProperty("recipient")
    private String recipient;

    /** WhatsApp Account ID del destinatario. */
    @JsonProperty("waId")
    private String waId;

    /** Namespace de identidad AJO. */
    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("journeyId")
    private String journeyId;

    @JsonProperty("journeyVersionId")
    private String journeyVersionId;

    @JsonProperty("journeyVersionName")
    private String journeyVersionName;

    @JsonProperty("journeyInstanceId")
    private String journeyInstanceId;

    @JsonProperty("journeyNodeId")
    private String journeyNodeId;

    @JsonProperty("journeyNodeName")
    private String journeyNodeName;

    @JsonProperty("journeyActionId")
    private String journeyActionId;

    @JsonProperty("journeyActionName")
    private String journeyActionName;

    @JsonProperty("templateName")
    private String templateName;

    @JsonProperty("templateCategory")
    private String templateCategory;

    @JsonProperty("sandboxName")
    private String sandboxName;

    /**
     * Tipo de ejecución: NATIVE | CUSTOM.
     * Controla si el callback se reenvía al AJO Webhook API.
     */
    @JsonProperty("executionType")
    private String executionType;

    /** UUID generado por Function 1 para trazabilidad interna BCP. */
    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Estado de correlación: STORED | CORRELATED | PARTIALLY_CORRELATED | UNCORRELATED
     * Se actualiza cada vez que llega un callback de Meta.
     */
    @JsonProperty("correlationStatus")
    private String correlationStatus;

    /** Último tipo de evento procesado (ej: "bcp.whatsapp.delivery.delivered"). */
    @JsonProperty("lastEventType")
    private String lastEventType;

    /** Timestamp ISO-8601 del último evento procesado. */
    @JsonProperty("lastEventAt")
    private String lastEventAt;

    /** Timestamp ISO-8601 informado por Meta en el último callback. */
    @JsonProperty("lastMetaTimestamp")
    private String lastMetaTimestamp;

    /** Timestamp ISO-8601 de creación del documento. */
    @JsonProperty("createdAt")
    private String createdAt;

    /** Timestamp ISO-8601 de última actualización del documento. */
    @JsonProperty("updatedAt")
    private String updatedAt;

    /**
     * Time-To-Live en segundos. Cosmos elimina el documento automáticamente.
     * Default: 604800 (7 días). -1 = sin expiración.
     */
    @JsonProperty("ttl")
    private int ttl = 604800;

    // ────────────────── Getters y Setters ──────────────────

    public String getId()              { return id; }
    public void setId(String v)        { this.id = v; }

    public String getWamid()           { return wamid; }
    public void setWamid(String v)     { this.wamid = v; }

    public String getCustomerId()      { return customerId; }
    public void setCustomerId(String v){ this.customerId = v; }

    public String getRecipient()       { return recipient; }
    public void setRecipient(String v) { this.recipient = v; }

    public String getWaId()            { return waId; }
    public void setWaId(String v)      { this.waId = v; }

    public String getNamespace()       { return namespace; }
    public void setNamespace(String v) { this.namespace = v; }

    public String getJourneyId()       { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }

    public String getJourneyVersionId()    { return journeyVersionId; }
    public void setJourneyVersionId(String v){ this.journeyVersionId = v; }

    public String getJourneyVersionName()  { return journeyVersionName; }
    public void setJourneyVersionName(String v){ this.journeyVersionName = v; }

    public String getJourneyInstanceId()   { return journeyInstanceId; }
    public void setJourneyInstanceId(String v){ this.journeyInstanceId = v; }

    public String getJourneyNodeId()   { return journeyNodeId; }
    public void setJourneyNodeId(String v){ this.journeyNodeId = v; }

    public String getJourneyNodeName() { return journeyNodeName; }
    public void setJourneyNodeName(String v){ this.journeyNodeName = v; }

    public String getJourneyActionId() { return journeyActionId; }
    public void setJourneyActionId(String v){ this.journeyActionId = v; }

    public String getJourneyActionName(){ return journeyActionName; }
    public void setJourneyActionName(String v){ this.journeyActionName = v; }

    public String getTemplateName()    { return templateName; }
    public void setTemplateName(String v){ this.templateName = v; }

    public String getTemplateCategory(){ return templateCategory; }
    public void setTemplateCategory(String v){ this.templateCategory = v; }

    public String getSandboxName()     { return sandboxName; }
    public void setSandboxName(String v){ this.sandboxName = v; }

    public String getExecutionType()   { return executionType; }
    public void setExecutionType(String v){ this.executionType = v; }

    public String getCorrelationId()   { return correlationId; }
    public void setCorrelationId(String v){ this.correlationId = v; }

    public String getCorrelationStatus(){ return correlationStatus; }
    public void setCorrelationStatus(String v){ this.correlationStatus = v; }

    public String getLastEventType()   { return lastEventType; }
    public void setLastEventType(String v){ this.lastEventType = v; }

    public String getLastEventAt()     { return lastEventAt; }
    public void setLastEventAt(String v){ this.lastEventAt = v; }

    public String getLastMetaTimestamp(){ return lastMetaTimestamp; }
    public void setLastMetaTimestamp(String v){ this.lastMetaTimestamp = v; }

    public String getCreatedAt()       { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }

    public String getUpdatedAt()       { return updatedAt; }
    public void setUpdatedAt(String v) { this.updatedAt = v; }

    public int getTtl()                { return ttl; }
    public void setTtl(int v)          { this.ttl = v; }
}

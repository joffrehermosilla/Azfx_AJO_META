package com.bcp.ajo.azfx.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AjoContextRequest — Contrato A: Custom Action 2 → Azure Function 1
 *
 * Payload que envía AJO mediante la segunda Custom Action del Journey.
 * Contiene el WAMID generado por Meta (obtenido de la respuesta de CA1)
 * y el contexto de ejecución del Journey.
 *
 * Campos obligatorios: wamid, recipient, journeyId, journeyVersionId.
 * Campos opcionales (pueden ser null si AJO no los inyecta): messageStatus, waId.
 *
 * Referencia BCP: "Custom Action Correlation — Envío WAMID + contexto AJO"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AjoContextRequest {

    /** WAMID generado por Meta al enviar el mensaje. Clave de correlación principal. */
    @JsonProperty("wamid")
    private String wamid;

    /** Número de teléfono del destinatario en formato E.164 sin '+' (ej: 51999999999). */
    @JsonProperty("recipient")
    @JsonAlias({"phone"})
    private String recipient;

    /** WhatsApp Account ID del destinatario (waId). Puede ser igual a recipient. */
    @JsonProperty("waId")
    private String waId;

    /** Estado del mensaje devuelto por Meta al momento del envío (ej: "accepted"). */
    @JsonProperty("messageStatus")
    private String messageStatus;

    /** ID del cliente BCP (CICID). Primary identity para CDP. */
    @JsonProperty("profileId")
    @JsonAlias({"customerId"})
    private String profileId;

    /** Namespace de identidad (ej: "CICID", "email"). */
    @JsonProperty("namespace")
    private String namespace;

    /** ID del Journey en AJO. */
    @JsonProperty("journeyId")
    private String journeyId;

    /** ID de versión del Journey. */
    @JsonProperty("journeyVersionId")
    private String journeyVersionId;

    /** Nombre de versión del Journey. */
    @JsonProperty("journeyVersionName")
    private String journeyVersionName;

    /** ID de instancia de ejecución del Journey (profileId+journeyId). */
    @JsonProperty("journeyInstanceId")
    private String journeyInstanceId;

    /** ID del nodo del Journey donde se ejecutó el envío. */
    @JsonProperty("journeyNodeId")
    private String journeyNodeId;

    /** Nombre legible del nodo del Journey. */
    @JsonProperty("journeyNodeName")
    private String journeyNodeName;

    /** ID de la Custom Action configurada en AJO. */
    @JsonProperty("journeyActionId")
    private String journeyActionId;

    /** Nombre de la Custom Action configurada en AJO. */
    @JsonProperty("journeyActionName")
    private String journeyActionName;

    /** Nombre del template de WhatsApp utilizado (ej: "loans"). */
    @JsonProperty("templateName")
    private String templateName;

    /** Categoría del template Meta (MARKETING, UTILITY, AUTHENTICATION). */
    @JsonProperty("templateCategory")
    private String templateCategory;

    /** Sandbox de AJO donde se ejecutó el Journey (ej: "prod", "dev"). */
    @JsonProperty("sandboxName")
    private String sandboxName;

    /**
     * Tipo de ejecución: NATIVE o CUSTOM.
     * Determina si el evento de callback se reenvía también al AJO Webhook API.
     */
    @JsonProperty("type")
    @JsonAlias({"executionType"})
    private String type;

    // ────────────────── Getters y Setters ──────────────────

    public String getWamid()              { return wamid; }
    public void setWamid(String v)        { this.wamid = v; }

    public String getRecipient()          { return recipient; }
    public void setRecipient(String v)    { this.recipient = v; }

    public String getWaId()               { return waId; }
    public void setWaId(String v)         { this.waId = v; }

    public String getMessageStatus()      { return messageStatus; }
    public void setMessageStatus(String v){ this.messageStatus = v; }

    public String getProfileId()          { return profileId; }
    public void setProfileId(String v)    { this.profileId = v; }

    public String getNamespace()          { return namespace; }
    public void setNamespace(String v)    { this.namespace = v; }

    public String getJourneyId()          { return journeyId; }
    public void setJourneyId(String v)    { this.journeyId = v; }

    public String getJourneyVersionId()   { return journeyVersionId; }
    public void setJourneyVersionId(String v){ this.journeyVersionId = v; }

    public String getJourneyVersionName() { return journeyVersionName; }
    public void setJourneyVersionName(String v){ this.journeyVersionName = v; }

    public String getJourneyInstanceId()  { return journeyInstanceId; }
    public void setJourneyInstanceId(String v){ this.journeyInstanceId = v; }

    public String getJourneyNodeId()      { return journeyNodeId; }
    public void setJourneyNodeId(String v){ this.journeyNodeId = v; }

    public String getJourneyNodeName()    { return journeyNodeName; }
    public void setJourneyNodeName(String v){ this.journeyNodeName = v; }

    public String getJourneyActionId()    { return journeyActionId; }
    public void setJourneyActionId(String v){ this.journeyActionId = v; }

    public String getJourneyActionName()  { return journeyActionName; }
    public void setJourneyActionName(String v){ this.journeyActionName = v; }

    public String getTemplateName()       { return templateName; }
    public void setTemplateName(String v) { this.templateName = v; }

    public String getTemplateCategory()   { return templateCategory; }
    public void setTemplateCategory(String v){ this.templateCategory = v; }

    public String getSandboxName()        { return sandboxName; }
    public void setSandboxName(String v)  { this.sandboxName = v; }

    public String getType()               { return type; }
    public void setType(String v)         { this.type = v; }
}

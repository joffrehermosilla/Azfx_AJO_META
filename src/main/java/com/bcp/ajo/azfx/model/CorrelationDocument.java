package com.bcp.ajo.azfx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operational correlation record stored in Cosmos DB, partition key /wamid. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CorrelationDocument {
    @JsonProperty("id")
    private String id;
    @JsonProperty("wamid")
    private String wamid;
    @JsonProperty("customerId")
    private String customerId;
    @JsonProperty("recipient")
    private String recipient;
    @JsonProperty("waId")
    private String waId;
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
    @JsonProperty("templateWamId")
    private String templateWamId;
    @JsonProperty("campaignId")
    private String campaignId;
    @JsonProperty("messageProfileId")
    private String messageProfileId;
    @JsonProperty("isTestExecution")
    private Boolean isTestExecution = false;
    @JsonProperty("templateCategory")
    private String templateCategory;
    @JsonProperty("sandboxName")
    private String sandboxName;
    @JsonProperty("executionType")
    private String executionType;
    @JsonProperty("correlationId")
    private String correlationId;
    @JsonProperty("correlationStatus")
    private String correlationStatus;
    @JsonProperty("lastEventType")
    private String lastEventType;
    @JsonProperty("lastEventAt")
    private String lastEventAt;
    @JsonProperty("lastMetaTimestamp")
    private String lastMetaTimestamp;
    @JsonProperty("createdAt")
    private String createdAt;
    @JsonProperty("updatedAt")
    private String updatedAt;
    @JsonProperty("ttl")
    private int ttl = 604800;

    public String getId() {
        return id;
    }

    public void setId(String v) {
        id = v;
    }

    public String getWamid() {
        return wamid;
    }

    public void setWamid(String v) {
        wamid = v;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String v) {
        customerId = v;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String v) {
        recipient = v;
    }

    public String getWaId() {
        return waId;
    }

    public void setWaId(String v) {
        waId = v;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String v) {
        namespace = v;
    }

    public String getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(String v) {
        journeyId = v;
    }

    public String getJourneyVersionId() {
        return journeyVersionId;
    }

    public void setJourneyVersionId(String v) {
        journeyVersionId = v;
    }

    public String getJourneyVersionName() {
        return journeyVersionName;
    }

    public void setJourneyVersionName(String v) {
        journeyVersionName = v;
    }

    public String getJourneyInstanceId() {
        return journeyInstanceId;
    }

    public void setJourneyInstanceId(String v) {
        journeyInstanceId = v;
    }

    public String getJourneyNodeId() {
        return journeyNodeId;
    }

    public void setJourneyNodeId(String v) {
        journeyNodeId = v;
    }

    public String getJourneyNodeName() {
        return journeyNodeName;
    }

    public void setJourneyNodeName(String v) {
        journeyNodeName = v;
    }

    public String getJourneyActionId() {
        return journeyActionId;
    }

    public void setJourneyActionId(String v) {
        journeyActionId = v;
    }

    public String getJourneyActionName() {
        return journeyActionName;
    }

    public void setJourneyActionName(String v) {
        journeyActionName = v;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String v) {
        templateName = v;
    }

    public String getTemplateWamId() {
        return templateWamId;
    }

    public void setTemplateWamId(String v) {
        templateWamId = v;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String v) {
        campaignId = v;
    }

    public String getMessageProfileId() {
        return messageProfileId;
    }

    public void setMessageProfileId(String v) {
        messageProfileId = v;
    }

    public Boolean getIsTestExecution() {
        return isTestExecution;
    }

    public void setIsTestExecution(Boolean v) {
        isTestExecution = v;
    }

    public String getTemplateCategory() {
        return templateCategory;
    }

    public void setTemplateCategory(String v) {
        templateCategory = v;
    }

    public String getSandboxName() {
        return sandboxName;
    }

    public void setSandboxName(String v) {
        sandboxName = v;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String v) {
        executionType = v;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String v) {
        correlationId = v;
    }

    public String getCorrelationStatus() {
        return correlationStatus;
    }

    public void setCorrelationStatus(String v) {
        correlationStatus = v;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public void setLastEventType(String v) {
        lastEventType = v;
    }

    public String getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(String v) {
        lastEventAt = v;
    }

    public String getLastMetaTimestamp() {
        return lastMetaTimestamp;
    }

    public void setLastMetaTimestamp(String v) {
        lastMetaTimestamp = v;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String v) {
        createdAt = v;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String v) {
        updatedAt = v;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int v) {
        ttl = v;
    }
}

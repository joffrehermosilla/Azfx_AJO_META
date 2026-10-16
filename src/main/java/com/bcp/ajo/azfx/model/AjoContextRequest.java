package com.bcp.ajo.azfx.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contract A: AJO Custom Action 2 -> Azure Function /api/ajo-context.
 * New optional fields are persisted only when they are supplied by AJO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AjoContextRequest {
    @JsonProperty("wamid")
    private String wamid;
    @JsonProperty("recipient")
    @JsonAlias({ "phone" })
    private String recipient;
    @JsonProperty("waId")
    private String waId;
    @JsonProperty("messageStatus")
    private String messageStatus;
    @JsonProperty("profileId")
    @JsonAlias({ "customerId" })
    private String profileId;
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
    @JsonProperty("type")
    @JsonAlias({ "executionType" })
    private String type;

    // Optional source values for the expanded CDP schema; do not invent these in
    // the mapper.
    @JsonProperty("campaignId")
    private String campaignId;
    @JsonProperty("templateWamId")
    private String templateWamId;
    @JsonProperty("messageProfileId")
    private String messageProfileId;
    @JsonProperty("isTestExecution")
    private Boolean isTestExecution = false;

    public String getWamid() {
        return wamid;
    }

    public void setWamid(String v) {
        wamid = v;
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

    public String getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(String v) {
        messageStatus = v;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String v) {
        profileId = v;
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

    public String getType() {
        return type;
    }

    public void setType(String v) {
        type = v;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String v) {
        campaignId = v;
    }

    public String getTemplateWamId() {
        return templateWamId;
    }

    public void setTemplateWamId(String v) {
        templateWamId = v;
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
}

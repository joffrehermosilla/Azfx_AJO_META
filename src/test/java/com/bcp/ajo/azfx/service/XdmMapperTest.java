package com.bcp.ajo.azfx.service;

import com.bcp.ajo.azfx.model.CorrelationDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XdmMapperTest {

    private XdmMapper xdmMapper;

    @BeforeEach
    void setUp() {
        xdmMapper = new XdmMapper();
    }

    private CorrelationDocument createSampleCorrelation(String executionType) {
        CorrelationDocument doc = new CorrelationDocument();
        doc.setWamid("wamid.HBgLM...12345");
        doc.setCustomerId("BCP_CLI_998877");
        doc.setRecipient("51987654321");
        doc.setJourneyId("journey-campania-tc-v1");
        doc.setJourneyVersionId("v1.0.0");
        doc.setJourneyVersionName("Campaña Tarjeta de Crédito 2026");
        doc.setJourneyNodeId("node_send_whatsapp_01");
        doc.setJourneyActionId("action_meta_push");
        doc.setTemplateName("bcp_oferta_tc_desa_v1");
        doc.setExecutionType(executionType);
        doc.setCorrelationId("corr-uuid-112233");
        doc.setCorrelationStatus("CORRELATED");
        return doc;
    }

    @Test
    @DisplayName("buildDeliveryXdm genera schema correcto para status delivered")
    void shouldBuildDeliveryXdmForDeliveredStatus() {
        CorrelationDocument doc = createSampleCorrelation("CUSTOM");

        Map<String, Object> xdm = xdmMapper.buildDeliveryXdm(
            doc,
            "delivered",
            "wamid.HBgLM...12345",
            "1728100000",
            null,
            null
        );

        assertNotNull(xdm.get("_id"));
        assertNotNull(xdm.get("timestamp"));
        assertEquals("bcp.whatsapp.delivery.delivered", xdm.get("eventType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
        assertNotNull(bcp);

        @SuppressWarnings("unchecked")
        Map<String, Object> identity = (Map<String, Object>) bcp.get("identity");
        assertEquals("BCP_CLI_998877", identity.get("customerId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");
        assertNotNull(whatsapp);

        @SuppressWarnings("unchecked")
        Map<String, Object> delivery = (Map<String, Object>) whatsapp.get("delivery");
        assertEquals("delivered", delivery.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> journeyCtx = (Map<String, Object>) whatsapp.get("journeyContext");
        assertEquals("journey-campania-tc-v1", journeyCtx.get("journeyId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) whatsapp.get("correlation");
        assertEquals("CORRELATED", correlation.get("correlationStatus"));
        assertEquals("CUSTOM", correlation.get("executionType"));
    }

    @Test
    @DisplayName("buildDeliveryXdm mapea evento failed con código y mensaje de error")
    void shouldBuildDeliveryXdmForFailedStatus() {
        CorrelationDocument doc = createSampleCorrelation("NATIVE");

        Map<String, Object> xdm = xdmMapper.buildDeliveryXdm(
            doc,
            "failed",
            "wamid.HBgLM...12345",
            "1728100000",
            "131026",
            "Receiver is incapable of receiving this message"
        );

        assertEquals("bcp.whatsapp.delivery.failed", xdm.get("eventType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
        @SuppressWarnings("unchecked")
        Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");
        @SuppressWarnings("unchecked")
        Map<String, Object> delivery = (Map<String, Object>) whatsapp.get("delivery");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) delivery.get("error");

        assertEquals("131026", error.get("code"));
        assertEquals("Receiver is incapable of receiving this message", error.get("message"));
    }

    @Test
    @DisplayName("buildTrackingXdm genera evento para button_reply con buttonId y buttonTitle")
    void shouldBuildTrackingXdmForButtonReply() {
        CorrelationDocument doc = createSampleCorrelation("CUSTOM");

        Map<String, Object> xdm = xdmMapper.buildTrackingXdm(
            doc,
            "wamid.HBgLM...12345", // Outbound
            "wamid.HBgLI...67890", // Inbound
            "1728100500",
            "button_reply",
            "Lo quiero ahora",
            "btn_aceptar_oferta",
            "Lo quiero ahora"
        );

        assertEquals("bcp.whatsapp.tracking.button_reply", xdm.get("eventType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
        @SuppressWarnings("unchecked")
        Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) whatsapp.get("message");
        assertEquals("wamid.HBgLM...12345", message.get("wamId"));
        assertEquals("wamid.HBgLI...67890", message.get("messageId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tracking = (Map<String, Object>) whatsapp.get("tracking");
        assertEquals("button_reply", tracking.get("type"));
        assertEquals("btn_aceptar_oferta", tracking.get("buttonId"));
        assertEquals("Lo quiero ahora", tracking.get("buttonTitle"));
        assertEquals("Lo quiero ahora", tracking.get("reply"));
    }

    @Test
    @DisplayName("buildDeliveryXdm maneja evento UNCORRELATED correctamente sin NPE")
    void shouldHandleUncorrelatedDeliveryGracefully() {
        Map<String, Object> xdm = xdmMapper.buildDeliveryXdm(
            null, // Sin documento de correlación previo en Cosmos
            "sent",
            "wamid.HBgLM...desconocido",
            "1728100000",
            null,
            null
        );

        assertEquals("bcp.whatsapp.delivery.sent", xdm.get("eventType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bcp = (Map<String, Object>) xdm.get("_bcp");
        @SuppressWarnings("unchecked")
        Map<String, Object> identity = (Map<String, Object>) bcp.get("identity");
        assertNull(identity.get("customerId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> messaging = (Map<String, Object>) bcp.get("messaging");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) messaging.get("whatsapp");
        @SuppressWarnings("unchecked")
        Map<String, Object> correlation = (Map<String, Object>) whatsapp.get("correlation");
        assertEquals("UNCORRELATED", correlation.get("correlationStatus"));
    }
}

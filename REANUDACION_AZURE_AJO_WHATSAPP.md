# REANUDACION — BCP AJO WhatsApp Bidireccionalidad Azure Functions
# Documento para retomar configuración con otra IA
# Generado: 2026-10-06
# ═══════════════════════════════════════════════════════════════════════════

## RESUMEN EJECUTIVO

Se está implementando la capa middleware BCP en Microsoft Azure para
correlacionar eventos de WhatsApp (delivery y tracking) entre:
  - Adobe Journey Optimizer (AJO)
  - Meta WhatsApp Business Platform  
  - Adobe CDP (AEP Streaming Ingestion)

La arquitectura NO incluye IA, Google Maps ni ningún componente externo adicional en Fase 1.

## ARQUITECTURA COMPLETA

```
AJO Journey
  │
  ├── CA1: Custom Action "Envío WhatsApp" → Meta Cloud API
  │         Recibe: wamid (messages[0].id), recipient, messageStatus
  │
  └── CA2: Custom Action "Datos AJO a Middleware"
           POST https://func-ajo-whatsapp-dev.azurewebsites.net/api/ajo-context
           Headers: x-functions-key: <FUNCTION_KEY>
           Body: ver CONTRATO A abajo

Azure Function 1 — /api/ajo-context
  │   Persiste en Cosmos DB (container: message-correlation)
  │   Responde 200 con correlationId
  │
  └── Cosmos DB
        database: bcp-whatsapp
        container: message-correlation  (partitionKey=/wamid, TTL=604800)
        container: events               (partitionKey=/wamid, TTL=604800)
              ▲
              │ lookup por WAMID
Azure Function 2 — /api/meta-webhook
  ▲   GET: verificación Meta (hub.challenge)
  │   POST: callbacks Meta → valida firma → correlaciona → XDM → CDP
  │         Si executionType=NATIVE → relay a AJO Webhook API
  │
Meta Developers — Callback URL configurado en Meta Business Manager
  Delivery: sent / delivered / read / failed  (statuses[].id)
  Tracking: button_reply / text_reply         (messages[].context.id)
```

## REGLA DE WAMID (CRÍTICO)

```
DELIVERY  → statuses[].id           = WAMID OUTBOUND (clave de Cosmos lookup)
TRACKING  → messages[].context.id   = WAMID OUTBOUND (clave de Cosmos lookup)
            messages[].id           = WAMID INBOUND  (reply del usuario)
```

## CONTRATO A — CA2 → Function 1 (payload de Custom Action 2)

```json
{
  "wamid": "",           // OBLIGATORIO — de la respuesta de CA1
  "recipient": "",       // Teléfono E.164 sin '+'
  "waId": "",            // WhatsApp Account ID
  "messageStatus": "",   // "accepted" o similar
  "profileId": "",       // CICID — customerId BCP (Primary Identity CDP)
  "namespace": "",       // "CICID"
  "journeyId": "",       // De propiedades AJO
  "journeyVersionId": "",
  "journeyVersionName": "",
  "journeyInstanceId": "",
  "journeyNodeId": "",
  "journeyNodeName": "",
  "journeyActionId": "",
  "journeyActionName": "",
  "templateName": "",
  "templateCategory": "", // MARKETING | UTILITY | AUTHENTICATION
  "sandboxName": "",
  "type": "CUSTOM"        // CUSTOM | NATIVE — controla relay a AJO Webhook
}
```

## CONTRATO B — Documento Cosmos DB (message-correlation)

```json
{
  "id": "wamid.OUTBOUND",
  "wamid": "wamid.OUTBOUND",
  "customerId": "CICID",
  "recipient": "51999999999",
  "waId": "51999999999",
  "namespace": "CICID",
  "journeyId": "J001",
  "journeyVersionId": "V003",
  "journeyVersionName": "Loan Offer Journey v3",
  "journeyInstanceId": "JI-998877",
  "journeyNodeId": "N004",
  "journeyNodeName": "send_whatsapp_offer",
  "journeyActionId": "whatsapp_custom_action_01",
  "journeyActionName": "Envío WhatsApp",
  "templateName": "loans",
  "templateCategory": "MARKETING",
  "sandboxName": "prod",
  "executionType": "CUSTOM",
  "correlationId": "uuid-generado-por-function1",
  "correlationStatus": "STORED",
  "lastEventType": null,
  "lastEventAt": null,
  "lastMetaTimestamp": null,
  "createdAt": "2026-10-06T...",
  "updatedAt": "2026-10-06T...",
  "ttl": 604800
}
```

## CONTRATO C — XDM ExperienceEvent → CDP

Schema: `_bcp.messaging.whatsapp.*`

### Delivery:
```json
{
  "_id": "uuid",
  "timestamp": "ISO-8601",
  "eventType": "bcp.whatsapp.delivery.delivered",
  "_bcp": {
    "event": { "name": "messaging.whatsapp.log" },
    "identity": { "customerId": "CICID" },
    "messaging": {
      "whatsapp": {
        "audit": {
          "metaTimestamp": "...",
          "receivedTimestamp": "...",
          "source": "meta_webhook"
        },
        "message": {
          "wamId": "wamid.OUTBOUND",
          "templateName": "loans",
          "templateWamId": "...",
          "messageId": null
        },
        "journeyContext": {
          "journeyId": "J001",
          "journeyVersionId": "V003",
          "journeyVersionName": "...",
          "journeyNodeId": "N004",
          "journeyActionId": "..."
        },
        "correlation": {
          "correlationId": "uuid",
          "correlationStatus": "CORRELATED",
          "executionType": "CUSTOM"
        },
        "delivery": {
          "status": "delivered",
          "error": { "code": null, "message": null }
        }
      }
    }
  }
}
```

### Tracking:
```json
{
  "_id": "uuid",
  "timestamp": "ISO-8601",
  "eventType": "bcp.whatsapp.tracking.button_reply",
  "_bcp": {
    "event": { "name": "messaging.whatsapp.log" },
    "identity": { "customerId": "CICID" },
    "messaging": {
      "whatsapp": {
        "audit": { "metaTimestamp": "...", "receivedTimestamp": "...", "source": "meta_webhook" },
        "message": {
          "wamId": "wamid.OUTBOUND",
          "messageId": "wamid.INBOUND",
          "templateName": "loans"
        },
        "journeyContext": { ... },
        "correlation": { "correlationId": "uuid", "correlationStatus": "CORRELATED", "executionType": "CUSTOM" },
        "tracking": {
          "type": "button_reply",
          "reply": "Quiero mi préstamo",
          "buttonId": "BTN_01",
          "buttonTitle": "Solicitar préstamo"
        }
      }
    }
  }
}
```

### Taxonomía eventType:
```
bcp.whatsapp.delivery.sent
bcp.whatsapp.delivery.delivered
bcp.whatsapp.delivery.read
bcp.whatsapp.delivery.failed
bcp.whatsapp.tracking.button_reply
bcp.whatsapp.tracking.text_reply
bcp.whatsapp.consent.opt_in
bcp.whatsapp.consent.opt_out
```

## ENDPOINTS CRÍTICOS

| Servicio | URL |
|---|---|
| CDP Ingest | https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d |
| CDP Flow ID | c508bc8f-964f-4e49-81e8-1142cc239a99 |
| AJO Webhook | https://platform-va7.adobe.io/journeys/webhooks/ingest/78dc043f-faf9-4490-882f-11285090a93d |
| Function 1 | POST https://func-ajo-whatsapp-dev.azurewebsites.net/api/ajo-context |
| Function 2 GET | GET https://func-ajo-whatsapp-dev.azurewebsites.net/api/meta-webhook |
| Function 2 POST | POST https://func-ajo-whatsapp-dev.azurewebsites.net/api/meta-webhook |

## ESTRUCTURA DE ARCHIVOS DEL PROYECTO

```
Azfx_AJO_META/
├── pom.xml                              Maven POM — Java 21, azure-functions, cosmos SDK
├── host.json                            Azure Functions runtime config
├── local.settings.json                  Variables de entorno locales
├── deploy/
│   └── azure-setup.sh                   Script Azure CLI para provisionar todo
└── src/main/java/com/bcp/ajo/azfx/
    ├── AjoContextFunction.java          FUNCTION 1: POST /api/ajo-context
    ├── MetaWebhookFunction.java         FUNCTION 2: GET|POST /api/meta-webhook
    ├── model/
    │   ├── AjoContextRequest.java       Contrato A (CA2 → Function 1)
    │   └── CorrelationDocument.java     Contrato B (Cosmos document)
    ├── service/
    │   ├── CosmosService.java           Cosmos DB — upsert, lookup, update
    │   ├── CdpService.java              AEP Streaming Ingestion
    │   ├── AjoWebhookService.java       AJO Webhook relay (solo NATIVE)
    │   └── XdmMapper.java               XDM builder (delivery + tracking)
    └── util/
        └── MetaSignatureValidator.java  HMAC-SHA256 Meta signature
```

## VARIABLES DE ENTORNO (Azure App Settings)

| Variable | Valor |
|---|---|
| COSMOS_ENDPOINT | https://cosmos-ajo-whatsapp-dev.documents.azure.com:443/ |
| COSMOS_KEY | Primary key del Cosmos account |
| COSMOS_DATABASE | bcp-whatsapp |
| COSMOS_CONTAINER_CORRELATION | message-correlation |
| COSMOS_CONTAINER_EVENTS | events |
| META_VERIFY_TOKEN | bcp_meta_verify_2026 |
| META_APP_SECRET | Secret de Meta Business Manager |
| CDP_INGEST_URL | https://dcs.adobedc.net/collection/e65e... |
| CDP_FLOW_ID | c508bc8f-964f-4e49-81e8-1142cc239a99 |
| CDP_BEARER_TOKEN | Bearer token IMS Adobe (rotar cada 24h) |
| AJO_WEBHOOK_URL | https://platform-va7.adobe.io/journeys/webhooks/... |
| AJO_BEARER_TOKEN | Bearer token AJO |
| FUNCTION_KEY | Key para autenticar CA2 contra Function 1 |

## CUTS DE IMPLEMENTACIÓN (en orden)

| Cut | Descripción | Estado |
|---|---|---|
| A0 | Congelar contratos CA1, CA2, Cosmos, XDM | ✅ DONE |
| A1 | Crear RG, Storage, Cosmos, Function App | ⏳ PENDIENTE |
| A2 | Probar insert/read Cosmos por WAMID | ⏳ PENDIENTE |
| A3 | Probar CA2 → Function 1 → Cosmos → 200 | ⏳ PENDIENTE |
| A4 | Configurar GET Meta → Function 2 → challenge | ⏳ PENDIENTE |
| A5 | Probar delivery Meta → Cosmos lookup → CDP | ⏳ PENDIENTE |
| A6 | Probar button_reply / text_reply → context.id → CDP | ⏳ PENDIENTE |
| A7 | Probar NATIVE → CDP + AJO Webhook relay | ⏳ PENDIENTE |
| A8 | Segunda plantilla (CDP → AJO event → CA → Meta) | ⏳ FASE 2 |
| A9 | Load test >= 200 TPS Function 1 | ⏳ FASE 2 |
| A10 | Managed Identity + Key Vault + Budget Alerts | ⏳ FASE 2 |

## COMANDOS PARA CONSTRUIR Y DESPLEGAR

```bash
# Build
cd Azfx_AJO_META
mvn clean package

# Correr localmente
mvn azure-functions:run

# Desplegar a Azure
mvn azure-functions:deploy

# Ver logs en vivo
az functionapp log tail --name func-ajo-whatsapp-dev --resource-group rg-ajo-whatsapp-desafunc-dev
```

## NOTAS IMPORTANTES

1. **WAMID es la clave de todo** — sin wamid no hay correlación
2. **TTL Cosmos = 604800 seg (7 días)** — histórico permanece en CDP
3. **Cosmos TTL debe estar habilitado** en el container (el script lo hace con --default-ttl)
4. **Solo se relay a AJO** cuando `executionType=NATIVE` — Custom no se garantiza
5. **CDP acepta hasta 1MB por payload** — 30 RPS límite BCP PROD
6. **Function 1 target < 300ms** — AJO penaliza endpoints > 750ms
7. **META_APP_SECRET vacío** → validación de firma deshabilitada (solo DEV)
8. **Bearer tokens expiran** — CDP y AJO requieren rotación periódica (< 24h IMS)
9. **Segunda plantilla = WAMID nuevo** — nunca reutilizar WAMID de template anterior
10. **No usar Cosmos como histórico** — solo es el store operacional de correlación

## PROYECTO QUARKUS DE REFERENCIA

El proyecto Quarkus anterior resolvió los mismos contratos conceptuales:
  - Quarkus WebhookController  → Azure MetaWebhookFunction
  - Quarkus CorrelationRepo    → Azure CosmosService
  - Quarkus TrackingEventMapper → Azure XdmMapper
  - Quarkus CDP Client         → Azure CdpService
  - Quarkus Native AJO Client  → Azure AjoWebhookService

La lógica de negocio es idéntica. Solo cambia la plataforma de ejecución.

## RPS Y CAPACIDAD

| Ambiente | RPS disponibles |
|---|---|
| DESA CDP | 100 |
| CERTI CDP | 100 |
| PROD CDP | 1,300 total (330 asignados, 970 disponibles) |

**Regla BCP**: 30 RPS downstream → throttle desde AJO (journey rate)
**AJO Custom Actions**: frontend debe soportar >= 200 TPS
**Solución**: Function 1 escala automáticamente (Flex Consumption). Cosmos absorbe burst.

---

## 📌 ESTADO EXACTO DE LA SESIÓN Y PASOS AL RETOMAR

### ✅ Lo que ya está completado y validado:
1. **Código Fuente Java 21 y Contratos**:
   - [`AjoContextFunction.java`](src/main/java/com/bcp/ajo/azfx/AjoContextFunction.java): Ingesta rápida (< 300 ms) desde CA2 hacia Cosmos DB.
   - [`MetaWebhookFunction.java`](src/main/java/com/bcp/ajo/azfx/MetaWebhookFunction.java): Validación GET (challenge) y POST (delivery + tracking + relay nativo).
   - [`AjoContextRequest.java`](src/main/java/com/bcp/ajo/azfx/model/AjoContextRequest.java): Modelo con `@JsonAlias` resiliente para `customerId`/`profileId`, `phone`/`recipient`, `executionType`/`type`.
   - [`CorrelationDocument.java`](src/main/java/com/bcp/ajo/azfx/model/CorrelationDocument.java): Entidad Cosmos DB con TTL de 7 días (`604800` seg) y partition key `/wamid`.
   - [`CosmosService.java`](src/main/java/com/bcp/ajo/azfx/service/CosmosService.java), [`XdmMapper.java`](src/main/java/com/bcp/ajo/azfx/service/XdmMapper.java), [`CdpService.java`](src/main/java/com/bcp/ajo/azfx/service/CdpService.java), [`AjoWebhookService.java`](src/main/java/com/bcp/ajo/azfx/service/AjoWebhookService.java).
   - [`MetaSignatureValidator.java`](src/main/java/com/bcp/ajo/azfx/util/MetaSignatureValidator.java): Validación HMAC-SHA256.
2. **Pruebas Automatizadas Unitarias**:
   - **17 de 17 tests pasando** (`mvn test` exitoso con BUILD SUCCESS).
3. **CI/CD y Empaquetado**:
   - Pipeline creado en [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml).
   - Empaquetado listo en `target/azure-functions/func-ajo-whatsapp-dev`.
4. **Infraestructura en Azure Cloud Shell**:
   - Suscripción activa: `f34b99ed-0989-45d2-ad7b-7132065a955b`.
   - Resource Group creado: `rg-ajo-whatsapp-desafunc-dev` (eastus).
   - Storage Account creado: `stajowhatsappdev01` (Succeeded).
   - Proveedores registrados: `Microsoft.DocumentDB`, `Microsoft.Web`, `Microsoft.Storage`.
5. **Documentación y Diagramas**:
   - [**`DIAGRAMA_ARQUITECTURA_BCP.md`**](DIAGRAMA_ARQUITECTURA_BCP.md): Diagramas Mermaid completos (Componentes, Secuencia Outbound, Secuencia Inbound Delivery, Tracking, Seguridad Key Vault y Pipeline CI/CD).
   - [**`README.md`**](README.md): Manual técnico con comandos de provisionamiento, App Settings y resolución de errores.

---

### 📋 Pasos exactos para ejecutar al retomar en Azure Cloud Shell:

```bash
# 1. Crear Cosmos DB Serverless con nombre limpio (cosmos-ajo-whatsapp-dev01)
az cosmosdb create \
  --name cosmos-ajo-whatsapp-dev01 \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --capabilities EnableServerless \
  --locations regionName=eastus

# 2. Crear Base de Datos y Colección con TTL de 7 días
az cosmosdb sql database create \
  --account-name cosmos-ajo-whatsapp-dev01 \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --name bcp-whatsapp

az cosmosdb sql container create \
  --account-name cosmos-ajo-whatsapp-dev01 \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --database-name bcp-whatsapp \
  --name message-correlation \
  --partition-key-path "/wamid" \
  --ttl 604800

# 3. Crear Azure Key Vault y almacenar secretos de forma segura
KEY_VAULT="kv-ajowhatsapp-dev01"
az keyvault create \
  --name "$KEY_VAULT" \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --location eastus \
  --enable-rbac-authorization false

COSMOS_KEY=$(az cosmosdb keys list --name cosmos-ajo-whatsapp-dev01 --resource-group rg-ajo-whatsapp-desafunc-dev --query "primaryMasterKey" -o tsv)

az keyvault secret set --vault-name "$KEY_VAULT" --name "CosmosKey" --value "$COSMOS_KEY"
az keyvault secret set --vault-name "$KEY_VAULT" --name "MetaAppSecret" --value "TU_META_APP_SECRET"
az keyvault secret set --vault-name "$KEY_VAULT" --name "MetaVerifyToken" --value "bcp_meta_verify_2026"
az keyvault secret set --vault-name "$KEY_VAULT" --name "CdpBearerToken" --value "TU_CDP_BEARER_TOKEN"
az keyvault secret set --vault-name "$KEY_VAULT" --name "AjoBearerToken" --value "TU_AJO_BEARER_TOKEN"

# 4. Crear Function App y habilitar Managed Identity
az functionapp create \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --storage-account stajowhatsappdev01 \
  --consumption-plan-location eastus \
  --runtime java \
  --runtime-version 21 \
  --os-type Linux \
  --functions-version 4

PRINCIPAL_ID=$(az functionapp identity assign \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --query "principalId" -o tsv)

az keyvault set-policy \
  --name "$KEY_VAULT" \
  --object-id "$PRINCIPAL_ID" \
  --secret-permissions get list

# 5. Configurar App Settings con referencias a Key Vault (@Microsoft.KeyVault)
az functionapp config appsettings set \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --settings \
    COSMOS_ENDPOINT="https://cosmos-ajo-whatsapp-dev01.documents.azure.com:443/" \
    COSMOS_KEY="@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=CosmosKey)" \
    COSMOS_DATABASE="bcp-whatsapp" \
    COSMOS_CONTAINER_CORRELATION="message-correlation" \
    COSMOS_CONTAINER_EVENTS="events" \
    META_VERIFY_TOKEN="@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=MetaVerifyToken)" \
    META_APP_SECRET="@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=MetaAppSecret)" \
    CDP_INGEST_URL="https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d" \
    CDP_FLOW_ID="c508bc8f-964f-4e49-81e8-1142cc239a99" \
    CDP_BEARER_TOKEN="@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=CdpBearerToken)" \
    AJO_WEBHOOK_URL="https://platform-va7.adobe.io/journeys/webhooks/ingest/78dc043f-faf9-4490-882f-11285090a93d" \
    AJO_BEARER_TOKEN="@Microsoft.KeyVault(VaultName=${KEY_VAULT};SecretName=AjoBearerToken)"

# 6. Obtener Publish Profile para GitHub Actions Secret
az functionapp deployment list-publishing-profiles \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --xml
```


# BCP — Middleware de Correlación WhatsApp AJO & Meta (Azure Functions)

Middleware cloud-native de alta concurrencia y baja latencia (< 300 ms) desarrollado en **Java 21** sobre **Azure Functions** y **Azure Cosmos DB NoSQL**. Conecta y correlaciona de manera bidireccional los envíos de campañas desde **Adobe Journey Optimizer (AJO)**, la plataforma **Meta WhatsApp Business Platform (Cloud API)** y **Adobe Experience Platform (AEP / CDP)**.

---

## 📑 Tabla de Contenidos
1. [Arquitectura y Flujo End-to-End](#-arquitectura-y-flujo-end-to-end)
2. [Estructura del Proyecto y Endpoints](#-estructura-del-proyecto-y-endpoints)
3. [Guía Paso a Paso de Aprovisionamiento en Azure](#-guía-paso-a-paso-de-aprovisionamiento-en-azure)
4. [Configuración de Variables de Entorno (App Settings)](#-configuración-de-variables-de-entorno-app-settings)
5. [CI/CD con GitHub Actions](#-cicd-con-github-actions)
6. [Pruebas Automatizadas y Compilación Local](#-pruebas-automatizadas-y-compilación-local)
7. [Solución de Problemas Frecuentes](#-solución-de-problemas-frecuentes)

---

## 🏛 Arquitectura y Flujo End-to-End

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                  FLUJO DE OUTBOUND                                     │
│                                                                                        │
│  [AJO Journey]                                                                         │
│       │                                                                                │
│       ├─► (Custom Action 1) ──► [Meta Cloud API] ──► Genera WAMID                      │
│       │                                │                                               │
│       └─► (Custom Action 2) ───────────┼────────────────────────────────┐              │
│                 │                      │                                │              │
│                 ▼                      │                                │              │
│    [Azure Function 1: ajo-context]     │                                │              │
│      - Recibe WAMID + 17 campos AJO    │                                │              │
│      - Genera correlationId (UUID)     │                                │              │
│      - Guarda en Cosmos DB (TTL 7 días)│                                │              │
│      - Retorna 200 OK (< 300 ms)       │                                │              │
└────────────────────────────────────────┼────────────────────────────────┼──────────────┘
                                         ▼                                │
┌─────────────────────────────────────────────────────────────────────────┼──────────────┐
│                                  FLUJO DE INBOUND (CALLBACKS)           │              │
│                                                                         │              │
│  [Meta Webhooks] (Delivery: sent/delivered/read/failed | Tracking: button/text reply)  │
│       │                                                                 │              │
│       ▼ (POST con header X-Hub-Signature-256)                          │              │
│  [Azure Function 2: meta-webhook]                                       │              │
│       │                                                                 │              │
│       ├─► 1. Valida firma criptográfica HMAC-SHA256                     │              │
│       ├─► 2. Extrae WAMID (statuses[].id o messages[].context.id)       │              │
│       ├─► 3. Busca en Cosmos DB por Partition Key (/wamid) ◄────────────┘              │
│       ├─► 4. Mapea al schema XDM BCP (_bcp.messaging.whatsapp.*)                       │
│       ├─► 5. Despacha evento a Adobe CDP Streaming Ingestion (dcs.adobedc.net)        │
│       ├─► 6. Si executionType == "NATIVE" ──► Reenvía al AJO Webhook Ingest API       │
│       ├─► 7. Actualiza estado en Cosmos DB a "CORRELATED"                              │
│       └─► 8. Responde 200 OK a Meta en < 2 segundos                                    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🧩 Estructura del Proyecto y Endpoints

### Endpoints Expuestos

| Función | Método | Ruta | Auth Level | Descripción |
|---|---|---|---|---|
| **`ajo-context`** | `POST` | `/api/ajo-context` | `Function` (`x-functions-key`) | Recibe WAMID y contexto AJO (Custom Action 2). Inserta en Cosmos DB con status `STORED`. |
| **`meta-webhook-verify`** | `GET` | `/api/meta-webhook` | `Anonymous` | Handshake de verificación de Meta (`hub.mode`, `hub.verify_token`, `hub.challenge`). |
| **`meta-webhook-callback`** | `POST` | `/api/meta-webhook` | `Anonymous` | Recibe eventos de entrega y respuestas de WhatsApp. Valida firma, correlaciona y despacha XDM a CDP. |

### Árbol de Código Fuente
```
Azfx_AJO_META/
├── .github/
│   └── workflows/
│       └── deploy.yml              # Pipeline CI/CD automático de GitHub Actions
├── deploy/
│   └── azure-setup.sh              # Script de provisionamiento Azure CLI
├── src/
│   ├── main/java/com/bcp/ajo/azfx/
│   │   ├── AjoContextFunction.java # Azure Function 1 (AJO -> Cosmos DB)
│   │   ├── MetaWebhookFunction.java# Azure Function 2 (Meta -> Cosmos -> CDP / AJO)
│   │   ├── model/
│   │   │   ├── AjoContextRequest.java    # Contrato A: Payload de CA2 (soporta alias)
│   │   │   └── CorrelationDocument.java  # Contrato B: Modelo en Cosmos DB (TTL 7 días)
│   │   ├── service/
│   │   │   ├── CosmosService.java        # Cliente Cosmos DB singleton & operaciones
│   │   │   ├── XdmMapper.java            # Contrato C: Mapeo de eventos al schema XDM
│   │   │   ├── CdpService.java           # Cliente HTTP Streaming Ingestion AEP
│   │   │   └── AjoWebhookService.java    # Relé de eventos nativos hacia AJO
│   │   └── util/
│   │       └── MetaSignatureValidator.java # Validación HMAC-SHA256 de Meta
│   └── test/java/com/bcp/ajo/azfx/       # 17 Pruebas Unitarias automatizadas
│       ├── AjoContextFunctionTest.java
│       ├── MetaWebhookFunctionTest.java
│       ├── service/XdmMapperTest.java
│       └── util/MetaSignatureValidatorTest.java
├── host.json                       # Configuración del host Azure Functions v4
├── local.settings.template.json    # Plantilla de variables de entorno para control de versiones
├── pom.xml                         # Configuración Maven (Java 21, Azure Functions Plugin, Shade)
└── README.md                       # Documentación técnica y operacional
```

---

## ☁️ Guía Paso a Paso de Aprovisionamiento en Azure

Ejecuta estos pasos en **Azure Cloud Shell** (seleccionando entorno `Bash`) o desde tu terminal local con `az cli`.

### 1. Establecer Suscripción y Registrar Proveedores
*(Obligatorio para cuentas nuevas o con créditos de $200 USD)*:

```bash
# Fijar suscripción activa
az account set --subscription f34b99ed-0989-45d2-ad7b-7132065a955b

# Habilitar Cosmos DB, Azure Functions y Azure Storage
az provider register --namespace Microsoft.DocumentDB
az provider register --namespace Microsoft.Web
az provider register --namespace Microsoft.Storage

# Verificar estado de registro (debe responder Registered o Registering)
az provider show --namespace Microsoft.DocumentDB --query "registrationState" -o tsv
```

### 2. Crear Grupo de Recursos y Almacenamiento

```bash
# Crear Grupo de Recursos
az group create --name rg-ajo-whatsapp-desafunc-dev --location eastus

# Crear Storage Account para Azure Functions
az storage account create \
  --name stajowhatsappdev01 \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --location eastus \
  --sku Standard_LRS
```

### 3. Crear Cosmos DB (Serverless) y Colección de Correlación

> [!NOTE]
> Si recibes el error `DatabaseAccount cosmos-ajo-whatsapp-dev is in a failed provisioning state`, se debe a que el primer intento ocurrió mientras el proveedor `Microsoft.DocumentDB` aún se registraba. Elimínalo con:
> ```bash
> az cosmosdb delete --name cosmos-ajo-whatsapp-dev --resource-group rg-ajo-whatsapp-desafunc-dev --yes
> ```

```bash
# Crear Cuenta Cosmos DB NoSQL en modo Serverless (pago por consumo exacto)
az cosmosdb create \
  --name cosmos-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --capabilities EnableServerless \
  --locations regionName=eastus

# Crear Base de Datos
az cosmosdb sql database create \
  --account-name cosmos-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --name bcp-whatsapp

# Crear Contenedor "message-correlation" con Partition Key "/wamid" y TTL de 7 días (604800 seg)
az cosmosdb sql container create \
  --account-name cosmos-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --database-name bcp-whatsapp \
  --name message-correlation \
  --partition-key-path "/wamid" \
  --ttl 604800
```

### 4. Crear Azure Key Vault y Almacenar Secretos

Para cumplir con las normas de seguridad bancaria BCP, ningún token ni clave secreta se almacena en texto plano en la Function App:

```bash
KEY_VAULT="kv-ajowhatsapp-dev01" # Debe ser único globalmente

# 1. Crear Key Vault
az keyvault create \
  --name "$KEY_VAULT" \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --location eastus \
  --enable-rbac-authorization false

# 2. Obtener clave de Cosmos y guardarla en Key Vault
COSMOS_KEY=$(az cosmosdb keys list --name cosmos-ajo-whatsapp-dev --resource-group rg-ajo-whatsapp-desafunc-dev --query "primaryMasterKey" -o tsv)

az keyvault secret set --vault-name "$KEY_VAULT" --name "CosmosKey" --value "$COSMOS_KEY"
az keyvault secret set --vault-name "$KEY_VAULT" --name "MetaAppSecret" --value "TU_META_APP_SECRET_DE_BUSINESS_MANAGER"
az keyvault secret set --vault-name "$KEY_VAULT" --name "MetaVerifyToken" --value "bcp_meta_verify_2026"
az keyvault secret set --vault-name "$KEY_VAULT" --name "CdpBearerToken" --value "TU_CDP_BEARER_TOKEN"
az keyvault secret set --vault-name "$KEY_VAULT" --name "AjoBearerToken" --value "TU_AJO_BEARER_TOKEN"
```

### 5. Crear la Function App (Linux, Java 21) y Habilitar Identidad Administrada

```bash
# 1. Crear Function App
az functionapp create \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --storage-account stajowhatsappdev01 \
  --consumption-plan-location eastus \
  --runtime java \
  --runtime-version 21 \
  --os-type Linux \
  --functions-version 4

# 2. Asignar Identidad Administrada (System-Assigned Managed Identity)
PRINCIPAL_ID=$(az functionapp identity assign \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --query "principalId" -o tsv)

# 3. Otorgar permisos a la Function App para leer secretos del Key Vault
az keyvault set-policy \
  --name "$KEY_VAULT" \
  --object-id "$PRINCIPAL_ID" \
  --secret-permissions get list
```

---

## 🔐 Configuración de Variables de Entorno con Referencias a Key Vault

En lugar de exponer las credenciales en la configuración de la Function App, se utilizan **Key Vault References** (`@Microsoft.KeyVault(...)`):

```bash
KEY_VAULT="kv-ajowhatsapp-dev01"
COSMOS_ENDPOINT="https://cosmos-ajo-whatsapp-dev.documents.azure.com:443/"

az functionapp config appsettings set \
  --name func-ajo-whatsapp-dev \
  --resource-group rg-ajo-whatsapp-desafunc-dev \
  --settings \
    COSMOS_ENDPOINT="$COSMOS_ENDPOINT" \
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
```

---

## 🚀 CI/CD con GitHub Actions

El repositorio incluye el flujo automatizado [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml).

### Cómo conectar GitHub con Azure:
1. **Obtener el Publish Profile desde Cloud Shell**:
   ```bash
   az functionapp deployment list-publishing-profiles \
     --name func-ajo-whatsapp-dev \
     --resource-group rg-ajo-whatsapp-desafunc-dev \
     --xml
   ```
   *(Copia todo el XML que aparece entre `<publishData>` y `</publishData>`)*.

2. **Crear el Secreto en GitHub**:
   - Ve a tu repositorio en GitHub: **Settings** ➔ **Secrets and variables** ➔ **Actions**.
   - Haz clic en **New repository secret**.
   - **Name**: `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`
   - **Value**: Pega el contenido XML completo.
   - Guarda el secreto.

3. **Despliegue Automático**:
   Al hacer un `git push` a `main` o `master`, GitHub Actions ejecutará automáticamente:
   - Configuración de JDK 21 Temurin.
   - Ejecución de los 17 tests unitarios (`mvn clean test`).
   - Empaquetado (`mvn package -DskipTests`).
   - Publicación directa a la Function App en Azure.

---

## 🧪 Pruebas Automatizadas y Compilación Local

### Ejecutar Pruebas Unitarias
El proyecto cuenta con 17 pruebas con Mockito y JUnit 5 que cubren:
- Validación de firmas HMAC-SHA256 (formatos válidos, firmas falsificadas, modo DEV).
- Contratos XDM para delivery (`sent`, `delivered`, `read`, `failed` con códigos de error).
- Contratos XDM para tracking (`button_reply`, `text_reply`).
- Ingesta de CA2 con manejo de alias (`customerId`/`profileId`, `phone`/`recipient`, `executionType`/`type`).
- Handshake de verificación de Meta (`hub.challenge`).
- Bifurcación de relé hacia AJO Webhook si `executionType == NATIVE`.

```bash
mvn clean test
```

### Empaquetado
```bash
mvn package
```
Genera los directorios con los archivos `function.json` y el fat jar en `target/azure-functions/func-ajo-whatsapp-dev`.

### Ejecutar Localmente con Azure Functions Core Tools
Copia `local.settings.template.json` a `local.settings.json`, completa tus credenciales y ejecuta:
```bash
mvn azure-functions:run
```
O directamente con Azure Functions CLI:
```bash
cd target/azure-functions/func-ajo-whatsapp-dev
func start
```

---

## 🛠 Solución de Problemas Frecuentes

| Error Observado | Causa | Solución |
|---|---|---|
| `SubscriptionNotFound` | Cloud Shell cambió el contexto de suscripción. | Ejecutar `az account set --subscription <ID_SUSCRIPCION>`. |
| `MissingSubscriptionRegistration: Microsoft.DocumentDB` | Proveedor no activado en la suscripción. | Ejecutar `az provider register --namespace Microsoft.DocumentDB`. Esperar 60 segundos. |
| HTTP 403 en `POST /api/meta-webhook` | Firma HMAC no coincide con `META_APP_SECRET`. | Verificar que `META_APP_SECRET` en App Settings coincida exactamente con el App Secret en Meta Business Manager. |
| HTTP 403 en `GET /api/meta-webhook` | El verify token enviado por Meta no coincide. | Asegurar que `META_VERIFY_TOKEN` configurado en Meta coincida con la variable de entorno. |
| HTTP 400 en `POST /api/ajo-context` | Falta el campo obligatorio `wamid` o `journeyId`. | Asegurar que la Custom Action 2 en AJO mapee el WAMID devuelto por CA1. |
| Eventos no correlacionados en CDP (`UNCORRELATED`) | El callback de Meta llegó antes que el POST de CA2. | Normal en latencias extremas. El diseño actual procesa el evento XDM con `UNCORRELATED` y cuando CA2 llega, actualiza el documento. |

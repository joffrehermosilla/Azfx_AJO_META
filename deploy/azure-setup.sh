#!/bin/bash
# ════════════════════════════════════════════════════════════════════════════
# azure-setup.sh — Provisioning completo BCP Azure Functions + Cosmos DB
# ════════════════════════════════════════════════════════════════════════════
#
# PREREQUISITOS:
#   - Azure CLI instalado: https://docs.microsoft.com/cli/azure/install-azure-cli
#   - az login ejecutado (cuenta con crédito activo)
#   - Java 21 + Maven instalados
#
# USO:
#   chmod +x azure-setup.sh
#   ./azure-setup.sh
#
# VARIABLES A REVISAR ANTES DE EJECUTAR:
#   COSMOS_KEY, META_VERIFY_TOKEN, META_APP_SECRET,
#   CDP_BEARER_TOKEN, AJO_BEARER_TOKEN
# ════════════════════════════════════════════════════════════════════════════

set -e

# ── CONFIGURACIÓN CENTRAL ──────────────────────────────────────────────────
SUBSCRIPTION_ID="TU_SUBSCRIPTION_ID"
RESOURCE_GROUP="rg-ajo-whatsapp-desafunc-dev"
LOCATION="eastus"
STORAGE_ACCOUNT="stajowhatsappdev01"
COSMOS_ACCOUNT="cosmos-ajo-whatsapp-dev"
COSMOS_DATABASE="bcp-whatsapp"
FUNCTION_APP="func-ajo-whatsapp-dev"
APP_INSIGHTS="appi-ajo-whatsapp-dev"

# Cosmos Containers
CONTAINER_CORRELATION="message-correlation"
CONTAINER_EVENTS="events"

# ── CREDENCIALES (reemplazar antes de ejecutar) ───────────────────────────
META_VERIFY_TOKEN="bcp_meta_verify_2026"
META_APP_SECRET="TU_META_APP_SECRET_DE_META_BUSINESS_MANAGER"
CDP_INGEST_URL="https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d"
CDP_FLOW_ID="c508bc8f-964f-4e49-81e8-1142cc239a99"
CDP_BEARER_TOKEN="TU_CDP_BEARER_TOKEN"
AJO_WEBHOOK_URL="https://platform-va7.adobe.io/journeys/webhooks/ingest/78dc043f-faf9-4490-882f-11285090a93d"
AJO_BEARER_TOKEN="TU_AJO_BEARER_TOKEN"

echo ""
echo "════════════════════════════════════════════════════"
echo "  BCP Azure Functions + Cosmos DB — SETUP"
echo "════════════════════════════════════════════════════"

# ── PASO 0: Seleccionar suscripción ──────────────────────────────────────
echo ""
echo "[0] Seleccionando suscripción..."
az account set --subscription "$SUBSCRIPTION_ID"
echo "    OK"

# ── PASO 1: Resource Group ────────────────────────────────────────────────
echo ""
echo "[1] Creando Resource Group: $RESOURCE_GROUP"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags proyecto=bcp-ajo-whatsapp ambiente=dev
echo "    OK"

# ── PASO 2: Storage Account (requerido por Azure Functions) ───────────────
echo ""
echo "[2] Creando Storage Account: $STORAGE_ACCOUNT"
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --tags proyecto=bcp-ajo-whatsapp
echo "    OK"

# ── PASO 3: Application Insights ─────────────────────────────────────────
echo ""
echo "[3] Creando Application Insights: $APP_INSIGHTS"
az monitor app-insights component create \
  --app "$APP_INSIGHTS" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --kind web \
  --tags proyecto=bcp-ajo-whatsapp

APP_INSIGHTS_KEY=$(az monitor app-insights component show \
  --app "$APP_INSIGHTS" \
  --resource-group "$RESOURCE_GROUP" \
  --query "instrumentationKey" --output tsv)
echo "    OK — instrumentationKey: $APP_INSIGHTS_KEY"

# ── PASO 4: Cosmos DB Account (NoSQL API) ────────────────────────────────
echo ""
echo "[4] Creando Cosmos DB Account: $COSMOS_ACCOUNT (esto tarda ~3 min)"
az cosmosdb create \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --locations regionName="$LOCATION" failoverPriority=0 isZoneRedundant=False \
  --default-consistency-level Session \
  --enable-free-tier true \
  --tags proyecto=bcp-ajo-whatsapp
echo "    OK"

# ── PASO 5: Cosmos DB Database ────────────────────────────────────────────
echo ""
echo "[5] Creando Database: $COSMOS_DATABASE"
az cosmosdb sql database create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --name "$COSMOS_DATABASE"
echo "    OK"

# ── PASO 6: Container message-correlation (partitionKey=/wamid, TTL=on) ──
echo ""
echo "[6] Creando Container: $CONTAINER_CORRELATION (partitionKey=/wamid)"
az cosmosdb sql container create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --database-name "$COSMOS_DATABASE" \
  --name "$CONTAINER_CORRELATION" \
  --partition-key-path "/wamid" \
  --throughput 400 \
  --default-ttl 604800
echo "    OK — TTL habilitado: 604800 seg (7 días)"

# ── PASO 7: Container events (partitionKey=/wamid, TTL=on) ────────────────
echo ""
echo "[7] Creando Container: $CONTAINER_EVENTS (partitionKey=/wamid)"
az cosmosdb sql container create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --database-name "$COSMOS_DATABASE" \
  --name "$CONTAINER_EVENTS" \
  --partition-key-path "/wamid" \
  --throughput 400 \
  --default-ttl 604800
echo "    OK — TTL habilitado: 604800 seg (7 días)"

# ── PASO 8: Obtener Cosmos endpoint y key ─────────────────────────────────
echo ""
echo "[8] Obteniendo Cosmos endpoint y primary key..."
COSMOS_ENDPOINT=$(az cosmosdb show \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --query "documentEndpoint" --output tsv)

COSMOS_KEY=$(az cosmosdb keys list \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --query "primaryMasterKey" --output tsv)

echo "    Endpoint: $COSMOS_ENDPOINT"
echo "    Key: [configurada en Function App settings]"

# ── PASO 9: Function App (Flex Consumption, Java 21) ─────────────────────
echo ""
echo "[9] Creando Function App: $FUNCTION_APP (Java 21, Flex Consumption)"
az functionapp create \
  --name "$FUNCTION_APP" \
  --resource-group "$RESOURCE_GROUP" \
  --storage-account "$STORAGE_ACCOUNT" \
  --runtime java \
  --runtime-version 21 \
  --functions-version 4 \
  --app-insights "$APP_INSIGHTS" \
  --os-type Linux \
  --tags proyecto=bcp-ajo-whatsapp
echo "    OK"

# ── PASO 10: Configurar App Settings (variables de entorno) ───────────────
echo ""
echo "[10] Configurando App Settings..."
az functionapp config appsettings set \
  --name "$FUNCTION_APP" \
  --resource-group "$RESOURCE_GROUP" \
  --settings \
    "COSMOS_ENDPOINT=$COSMOS_ENDPOINT" \
    "COSMOS_KEY=$COSMOS_KEY" \
    "COSMOS_DATABASE=$COSMOS_DATABASE" \
    "COSMOS_CONTAINER_CORRELATION=$CONTAINER_CORRELATION" \
    "COSMOS_CONTAINER_EVENTS=$CONTAINER_EVENTS" \
    "META_VERIFY_TOKEN=$META_VERIFY_TOKEN" \
    "META_APP_SECRET=$META_APP_SECRET" \
    "CDP_INGEST_URL=$CDP_INGEST_URL" \
    "CDP_FLOW_ID=$CDP_FLOW_ID" \
    "CDP_BEARER_TOKEN=$CDP_BEARER_TOKEN" \
    "AJO_WEBHOOK_URL=$AJO_WEBHOOK_URL" \
    "AJO_BEARER_TOKEN=$AJO_BEARER_TOKEN" \
    "FUNCTIONS_WORKER_RUNTIME=java" \
    "APPINSIGHTS_INSTRUMENTATIONKEY=$APP_INSIGHTS_KEY"
echo "    OK"

# ── PASO 11: Alertas de presupuesto ───────────────────────────────────────
echo ""
echo "[11] RECORDATORIO: Crear alertas de presupuesto en Azure Cost Management:"
echo "     Budget: USD 25 (alerta a USD 20)"
echo "     Budget: USD 50 (alerta a USD 45)"
echo "     Budget: USD 100 (alerta a USD 90)"
echo "     Budget: USD 150 (alerta a USD 140)"

# ── RESUMEN ───────────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════"
echo "  SETUP COMPLETADO"
echo "════════════════════════════════════════════════════"
echo ""
echo "  Function App URL:"
FUNCTION_URL=$(az functionapp show \
  --name "$FUNCTION_APP" \
  --resource-group "$RESOURCE_GROUP" \
  --query "defaultHostName" --output tsv)
echo "    https://$FUNCTION_URL"
echo ""
echo "  Function 1 (AJO Context):"
echo "    POST https://$FUNCTION_URL/api/ajo-context?code=<FUNCTION_KEY>"
echo ""
echo "  Function 2 (Meta Webhook):"
echo "    GET  https://$FUNCTION_URL/api/meta-webhook"
echo "    POST https://$FUNCTION_URL/api/meta-webhook"
echo ""
echo "  PRÓXIMOS PASOS:"
echo "    1. cd Azfx_AJO_META"
echo "    2. mvn clean package"
echo "    3. mvn azure-functions:deploy"
echo "    4. Configurar GET /api/meta-webhook en Meta Business Manager"
echo "    5. Configurar CA2 en AJO con POST /api/ajo-context?code=<KEY>"
echo ""

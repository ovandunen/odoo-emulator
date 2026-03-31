# Odoo Emulator — Quarkus

A local Odoo SaaS emulator for developing and testing the `recruiting` application
without a real Odoo instance. Runs on **port 8069** (Odoo default).

## Start

```bash
mvn quarkus:dev
```

---

## Authentication

All endpoints (except `/web/session/authenticate` and inbound webhooks) require an API key.

### Option A — Use the pre-seeded key (easiest for dev)

```
Authorization: Bearer emulator-api-key-dev-only
```

### Option B — Login to get a session key

```http
POST http://localhost:8069/web/session/authenticate
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "call",
  "params": {
    "db": "emulator",
    "login": "admin",
    "password": "admin"
  }
}
```

Response contains `result.session_id` — use it as `Bearer <session_id>`.

---

## JSON-RPC Endpoint (Odoo standard)

All model operations go through:

```
POST http://localhost:8069/web/dataset/call_kw
Authorization: Bearer <api-key>
```

### res.partner — search_read
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": { "model": "res.partner", "method": "search_read", "args": [], "kwargs": {} }
}
```

### res.partner — create
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "res.partner", "method": "create",
    "args": [{ "name": "New Agency", "email": "agency@example.com", "is_company": true }],
    "kwargs": {}
  }
}
```

### account.move — create invoice
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "account.move", "method": "create",
    "args": [{ "partner_id": 1, "move_type": "out_invoice", "amount_total": 149.00 }],
    "kwargs": {}
  }
}
```

### account.move — confirm/post invoice
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "account.move", "method": "action_post",
    "args": [[101]], "kwargs": {}
  }
}
```

### payment.transaction — create
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "payment.transaction", "method": "create",
    "args": [{ "invoice_id": 101, "amount": 149.00, "provider_code": "transfer" }],
    "kwargs": {}
  }
}
```

### payment.transaction — set state
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "payment.transaction", "method": "_set_done",
    "args": [[201]], "kwargs": {}
  }
}
```
Available state methods: `_set_done`, `_set_canceled`, `_set_error`, `_set_pending`, `_set_authorized`

### ir.attachment — upload document (base64)
```json
{
  "jsonrpc": "2.0", "method": "call",
  "params": {
    "model": "ir.attachment", "method": "create",
    "args": [{
      "name": "cv_applicant_42.pdf",
      "res_model": "res.partner",
      "res_id": 1,
      "mimetype": "application/pdf",
      "datas": "<base64-encoded-content>"
    }],
    "kwargs": {}
  }
}
```

---

## Document REST Endpoints

### Upload
```http
POST http://localhost:8069/web/content/upload
Authorization: Bearer <api-key>
Content-Type: application/json

{
  "name": "job_position_senior_dev.pdf",
  "res_model": "account.move",
  "res_id": 101,
  "mimetype": "application/pdf",
  "datas": "<base64-content>"
}
```

### Download (binary)
```http
GET http://localhost:8069/web/content/1001
Authorization: Bearer <api-key>
```

### Download (base64 JSON)
```http
GET http://localhost:8069/web/content/1001/base64
Authorization: Bearer <api-key>
```

### List attachments for a record
```http
GET http://localhost:8069/web/content?res_model=account.move&res_id=101
Authorization: Bearer <api-key>
```

### Delete
```http
DELETE http://localhost:8069/web/content/1001
Authorization: Bearer <api-key>
```

---

## Webhook Simulation

### Simulate Odoo confirming a payment (fires POST to your app)
```http
POST http://localhost:8069/api/webhook/trigger/payment-confirmed/201
Authorization: Bearer <api-key>
```

Your app must be running at `http://localhost:8080` and expose `POST /api/odoo/webhook`.
Override the target URL with `?targetUrl=http://your-app/your/endpoint`.

### Simulate payment failed
```http
POST http://localhost:8069/api/webhook/trigger/payment-failed/201
Authorization: Bearer <api-key>
```

### Simulate invoice fully paid
```http
POST http://localhost:8069/api/webhook/trigger/invoice-paid/101
Authorization: Bearer <api-key>
```

### Manually set transaction state
```http
POST http://localhost:8069/api/webhook/trigger/set-tx-state/201
Authorization: Bearer <api-key>

{ "state": "error" }
```

---

## Admin / Debug

```http
GET http://localhost:8069/emulator/info
GET http://localhost:8069/emulator/state/partners
GET http://localhost:8069/emulator/state/invoices
GET http://localhost:8069/emulator/state/transactions
GET http://localhost:8069/emulator/state/attachments
```

---

## Configuration (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `quarkus.http.port` | `8069` | Emulator port |
| `odoo.emulator.api-key` | `emulator-api-key-dev-only` | Pre-seeded dev API key |
| `odoo.emulator.target-webhook-url` | `http://localhost:8080/api/odoo/webhook` | Where to fire outbound webhooks |

---

## Seeded Data

| Type | ID | Description |
|---|---|---|
| Partner | 1 | Acme Recruiting GmbH |
| Partner | 2 | Talent Solutions AG |
| Invoice | 101 | Posted invoice, €149, partner 1 |
| Transaction | 201 | Done (paid), references invoice 101 |


## running postgres in docker first time
docker run --name odoo-postgres \
  -e POSTGRES_DB=${POSTGRES_DB} \
  -e POSTGRES_USER=${POSTGRES_USER} \
  -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
  -p 5432:5432 \
  -d postgres:latest

docker start odoo-postgres 



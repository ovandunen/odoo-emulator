package solutions.envision.odoo.emulator.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import solutions.envision.odoo.emulator.service.EmulatorStore;
import solutions.envision.odoo.emulator.service.WebhookDispatchService;

import java.util.Map;

/**
 * Two purposes:
 *
 * 1. RECEIVE webhooks FROM your Quarkus app (e.g. your app notifies Odoo about events)
 *    POST /api/webhook/odoo  — no auth required (as Odoo SaaS doesn't auth inbound)
 *
 * 2. TRIGGER outbound webhooks TO your app (simulate Odoo calling your app)
 *    POST /api/webhook/trigger/payment-confirmed/{txId}
 *    POST /api/webhook/trigger/payment-failed/{txId}
 *    POST /api/webhook/trigger/invoice-paid/{invoiceId}
 */
@Path("/api/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@JBossLog
public class WebhookResource {

    @Inject
    EmulatorStore store;

    @Inject
    WebhookDispatchService dispatcher;

    // ── Receive webhooks from your app ────────────────────────────────────────

    @POST
    @Path("/odoo")
    public Response receiveWebhook(Map<String, Object> payload) {
        log.infof("Webhook received from app: %s", payload);
        // Emulator accepts and logs; extend here to update state based on payload
        String event = payload.containsKey("event") ? payload.get("event").toString() : "unknown";
        return Response.ok(Map.of("result", "accepted", "event", event)).build();
    }

    // ── Trigger outbound webhooks to your app ─────────────────────────────────

    /**
     * Simulates Odoo calling your app to notify a payment was confirmed.
     * Fires a POST to your app's webhook endpoint with a payment.transaction payload.
     */
    @POST
    @Path("/trigger/payment-confirmed/{txId}")
    public Response triggerPaymentConfirmed(@PathParam("txId") int txId,
                                            @QueryParam("targetUrl") String targetUrl) {
        return store.getTransaction(txId).map(tx -> {
            store.updateTransactionState(txId, "done");
            Map<String, Object> payload = Map.of(
                    "event",       "payment.transaction.confirmed",
                    "id",          tx.getId(),
                    "reference",   tx.getReference() != null ? tx.getReference() : "",
                    "state",       "done",
                    "amount",      tx.getAmount(),
                    "currency_id", tx.getCurrencyId(),
                    "invoice_id",  tx.getInvoiceId()
            );
            String url = targetUrl != null ? targetUrl : dispatcher.getDefaultWebhookUrl();
            log.infof("Triggering payment-confirmed webhook → %s (txId=%d)", url, txId);
            dispatcher.dispatch(url, payload);
            return Response.ok(Map.of("triggered", true, "txId", txId, "targetUrl", url)).build();
        }).orElse(Response.status(404).entity(Map.of("error", "Transaction not found: " + txId)).build());
    }

    /**
     * Simulates Odoo notifying your app that a payment failed.
     */
    @POST
    @Path("/trigger/payment-failed/{txId}")
    public Response triggerPaymentFailed(@PathParam("txId") int txId,
                                         @QueryParam("targetUrl") String targetUrl) {
        return store.getTransaction(txId).map(tx -> {
            store.updateTransactionState(txId, "error");
            Map<String, Object> payload = Map.of(
                    "event",       "payment.transaction.failed",
                    "id",          tx.getId(),
                    "reference",   tx.getReference() != null ? tx.getReference() : "",
                    "state",       "error",
                    "amount",      tx.getAmount(),
                    "invoice_id",  tx.getInvoiceId()
            );
            String url = targetUrl != null ? targetUrl : dispatcher.getDefaultWebhookUrl();
            log.infof("Triggering payment-failed webhook → %s (txId=%d)", url, txId);
            dispatcher.dispatch(url, payload);
            return Response.ok(Map.of("triggered", true, "txId", txId, "targetUrl", url)).build();
        }).orElse(Response.status(404).entity(Map.of("error", "Transaction not found: " + txId)).build());
    }

    /**
     * Simulates Odoo notifying your app that an invoice was fully paid.
     */
    @POST
    @Path("/trigger/invoice-paid/{invoiceId}")
    public Response triggerInvoicePaid(@PathParam("invoiceId") int invoiceId,
                                       @QueryParam("targetUrl") String targetUrl) {
        return store.getInvoice(invoiceId).map(inv -> {
            inv.setPaymentState("paid");
            inv.setState("posted");
            Map<String, Object> payload = Map.of(
                    "event",         "account.move.paid",
                    "id",            inv.getId(),
                    "ref",           inv.getRef() != null ? inv.getRef() : "",
                    "payment_state", "paid",
                    "amount_total",  inv.getAmountTotal(),
                    "partner_id",    inv.getPartnerId()
            );
            String url = targetUrl != null ? targetUrl : dispatcher.getDefaultWebhookUrl();
            log.infof("Triggering invoice-paid webhook → %s (invoiceId=%d)", url, invoiceId);
            dispatcher.dispatch(url, payload);
            return Response.ok(Map.of("triggered", true, "invoiceId", invoiceId, "targetUrl", url)).build();
        }).orElse(Response.status(404).entity(Map.of("error", "Invoice not found: " + invoiceId)).build());
    }

    /** Manual state setter — useful for testing edge cases */
    @POST
    @Path("/trigger/set-tx-state/{txId}")
    public Response setTransactionState(@PathParam("txId") int txId,
                                        Map<String, Object> body) {
        String newState = body.containsKey("state") ? body.get("state").toString() : null;
        if (newState == null) {
            return Response.status(400).entity(Map.of("error", "state is required")).build();
        }
        return store.updateTransactionState(txId, newState)
                .map(tx -> Response.ok(Map.of("id", tx.getId(), "state", tx.getState())).build())
                .orElse(Response.status(404).entity(Map.of("error", "Transaction not found: " + txId)).build());
    }
}

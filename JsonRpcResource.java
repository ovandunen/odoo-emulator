package solutions.envision.odoo.emulator.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import solutions.envision.odoo.emulator.model.*;
import solutions.envision.odoo.emulator.service.EmulatorStore;

import java.util.*;

/**
 * Emulates Odoo's primary JSON-RPC endpoint used by XML-RPC clients and the
 * Odoo web client:
 *
 *   POST /web/dataset/call_kw
 *
 * Payload structure:
 * {
 *   "jsonrpc": "2.0",
 *   "method": "call",
 *   "params": {
 *     "model":  "res.partner",
 *     "method": "search_read",
 *     "args":   [ [[...domain...]], [...fields...] ],
 *     "kwargs": { "limit": 10 }
 *   }
 * }
 */
@Path("/web/dataset")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@JBossLog
public class JsonRpcResource {

    @Inject
    EmulatorStore store;

    @POST
    @Path("/call_kw")
    public Response callKw(Map<String, Object> body) {
        try {
            Map<String, Object> params = asMap(body.get("params"));
            String model  = str(params.get("model"));
            String method = str(params.get("method"));

            log.debugf("JSON-RPC call_kw: model=%s method=%s", model, method);

            Object result = switch (model) {
                case "res.partner"           -> handlePartner(method, params);
                case "account.move"          -> handleInvoice(method, params);
                case "payment.transaction"   -> handleTransaction(method, params);
                case "ir.attachment"         -> handleAttachment(method, params);
                default -> throw new IllegalArgumentException("Unknown model: " + model);
            };

            return Response.ok(jsonRpcResult(result)).build();

        } catch (IllegalArgumentException e) {
            log.warnf("Bad request: %s", e.getMessage());
            return Response.ok(jsonRpcError(100, e.getMessage())).build();
        } catch (Exception e) {
            log.errorf(e, "Internal emulator error");
            return Response.ok(jsonRpcError(200, "Internal emulator error: " + e.getMessage())).build();
        }
    }

    // ── res.partner ───────────────────────────────────────────────────────────

    private Object handlePartner(String method, Map<String, Object> params) {
        return switch (method) {
            case "search_read" -> store.searchPartners(null);
            case "read"        -> {
                List<Integer> ids = idList(params);
                yield ids.stream().map(store::getPartner).flatMap(Optional::stream).toList();
            }
            case "create"      -> store.createPartner(mapToPartner(firstKwarg(params)));
            case "write"       -> {
                List<Integer> ids = idList(params);
                OdooPartner patch = mapToPartner(firstKwarg(params));
                ids.forEach(id -> store.updatePartner(id, patch));
                yield true;
            }
            case "search"      -> store.searchPartners(null).stream().map(OdooPartner::getId).toList();
            default -> throw new IllegalArgumentException("Unsupported partner method: " + method);
        };
    }

    // ── account.move ──────────────────────────────────────────────────────────

    private Object handleInvoice(String method, Map<String, Object> params) {
        return switch (method) {
            case "search_read" -> store.searchInvoices(null);
            case "read"        -> {
                List<Integer> ids = idList(params);
                yield ids.stream().map(store::getInvoice).flatMap(Optional::stream).toList();
            }
            case "create"      -> store.createInvoice(mapToInvoice(firstKwarg(params)));
            case "action_post" -> {
                // Confirm / post the invoice
                List<Integer> ids = idList(params);
                ids.forEach(store::confirmInvoice);
                yield true;
            }
            case "write"       -> {
                List<Integer> ids = idList(params);
                Map<String,Object> vals = firstKwarg(params);
                ids.forEach(id -> store.getInvoice(id).ifPresent(inv -> {
                    if (vals.containsKey("state")) inv.setState(str(vals.get("state")));
                    if (vals.containsKey("payment_state")) inv.setPaymentState(str(vals.get("payment_state")));
                }));
                yield true;
            }
            case "search"      -> store.searchInvoices(null).stream().map(OdooInvoice::getId).toList();
            default -> throw new IllegalArgumentException("Unsupported invoice method: " + method);
        };
    }

    // ── payment.transaction ───────────────────────────────────────────────────

    private Object handleTransaction(String method, Map<String, Object> params) {
        return switch (method) {
            case "search_read" -> store.searchTransactions(null);
            case "read"        -> {
                List<Integer> ids = idList(params);
                yield ids.stream().map(store::getTransaction).flatMap(Optional::stream).toList();
            }
            case "create"      -> store.createTransaction(mapToTransaction(firstKwarg(params)));
            case "write"       -> {
                List<Integer> ids = idList(params);
                Map<String,Object> vals = firstKwarg(params);
                if (vals.containsKey("state")) {
                    String newState = str(vals.get("state"));
                    ids.forEach(id -> store.updateTransactionState(id, newState));
                }
                yield true;
            }
            case "_set_done"            -> setTxState(params, "done");
            case "_set_canceled"        -> setTxState(params, "cancel");
            case "_set_error"           -> setTxState(params, "error");
            case "_set_pending"         -> setTxState(params, "pending");
            case "_set_authorized"      -> setTxState(params, "authorized");
            case "search"               -> store.searchTransactions(null).stream().map(OdooPaymentTransaction::getId).toList();
            default -> throw new IllegalArgumentException("Unsupported transaction method: " + method);
        };
    }

    private boolean setTxState(Map<String, Object> params, String state) {
        idList(params).forEach(id -> store.updateTransactionState(id, state));
        return true;
    }

    // ── ir.attachment ─────────────────────────────────────────────────────────

    private Object handleAttachment(String method, Map<String, Object> params) {
        return switch (method) {
            case "create" -> {
                Map<String,Object> vals = firstKwarg(params);
                OdooAttachment att = new OdooAttachment();
                att.setName(str(vals.get("name")));
                att.setResModel(str(vals.get("res_model")));
                att.setResId(intVal(vals.get("res_id")));
                att.setMimetype(str(vals.getOrDefault("mimetype", "application/octet-stream")));
                att.setDescription(str(vals.get("description")));
                String datasB64 = str(vals.get("datas"));
                byte[] raw = datasB64 != null ? Base64.getDecoder().decode(datasB64) : new byte[0];
                att.setDatas(raw);
                att.setFileSize(raw.length);
                yield store.storeAttachment(att);
            }
            case "read", "search_read" -> {
                Object resModel = firstKwarg(params).get("res_model");
                Object resId    = firstKwarg(params).get("res_id");
                yield store.searchAttachments(
                        resModel != null ? str(resModel) : null,
                        resId    != null ? intVal(resId)  : null
                );
            }
            case "unlink" -> {
                idList(params).forEach(store::deleteAttachment);
                yield true;
            }
            default -> throw new IllegalArgumentException("Unsupported attachment method: " + method);
        };
    }

    // ── mappers ───────────────────────────────────────────────────────────────

    private OdooPartner mapToPartner(Map<String,Object> v) {
        OdooPartner p = new OdooPartner();
        p.setName(str(v.get("name")));
        p.setEmail(str(v.get("email")));
        p.setPhone(str(v.get("phone")));
        p.setStreet(str(v.get("street")));
        p.setCity(str(v.get("city")));
        p.setZip(str(v.get("zip")));
        if (v.containsKey("is_company")) p.setIsCompany(Boolean.parseBoolean(str(v.get("is_company"))));
        return p;
    }

    private OdooInvoice mapToInvoice(Map<String,Object> v) {
        OdooInvoice i = new OdooInvoice();
        if (v.containsKey("partner_id")) i.setPartnerId(intVal(v.get("partner_id")));
        i.setMoveType(str(v.getOrDefault("move_type", "out_invoice")));
        i.setRef(str(v.get("ref")));
        i.setName(str(v.get("name")));
        if (v.containsKey("amount_total")) i.setAmountTotal(doubleVal(v.get("amount_total")));
        i.setInvoiceDate(str(v.get("invoice_date")));
        i.setInvoiceDateDue(str(v.get("invoice_date_due")));
        return i;
    }

    private OdooPaymentTransaction mapToTransaction(Map<String,Object> v) {
        OdooPaymentTransaction tx = new OdooPaymentTransaction();
        if (v.containsKey("invoice_id")) tx.setInvoiceId(intVal(v.get("invoice_id")));
        if (v.containsKey("amount"))     tx.setAmount(doubleVal(v.get("amount")));
        if (v.containsKey("partner_id")) tx.setPartnerId(intVal(v.get("partner_id")));
        tx.setProviderCode(str(v.getOrDefault("provider_code", "transfer")));
        tx.setReference(str(v.get("reference")));
        tx.setCurrencyId(str(v.getOrDefault("currency_id", "EUR")));
        return tx;
    }

    // ── JSON-RPC helpers ──────────────────────────────────────────────────────

    private Map<String,Object> jsonRpcResult(Object result) {
        return Map.of("jsonrpc", "2.0", "id", 1, "result", result);
    }

    private Map<String,Object> jsonRpcError(int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", 1,
                "error", Map.of("code", code, "message", message,
                        "data", Map.of("name", "odoo.exceptions.UserError", "message", message)));
    }

    // ── utility ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String,Object> asMap(Object o) {
        return o instanceof Map<?,?> m ? (Map<String,Object>) m : Map.of();
    }

    private String str(Object o) { return o != null ? o.toString() : null; }

    private int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private double doubleVal(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> idList(Map<String,Object> params) {
        List<Object> args = (List<Object>) params.getOrDefault("args", List.of());
        if (!args.isEmpty() && args.get(0) instanceof List<?> ids) {
            return ids.stream().map(id -> intVal(id)).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> firstKwarg(Map<String,Object> params) {
        // For 'create'/'write': args[1] or kwargs.values
        List<Object> args = (List<Object>) params.getOrDefault("args", List.of());
        if (args.size() >= 2 && args.get(1) instanceof Map<?,?> m) return (Map<String,Object>) m;
        if (args.size() >= 1 && args.get(0) instanceof Map<?,?> m) return (Map<String,Object>) m;
        Map<String,Object> kwargs = asMap(params.get("kwargs"));
        if (kwargs.containsKey("values")) return asMap(kwargs.get("values"));
        return Map.of();
    }
}

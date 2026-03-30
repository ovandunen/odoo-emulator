package solutions.envision.odoo.emulator.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import solutions.envision.odoo.emulator.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central in-memory store for the Odoo emulator.
 * All data resets on restart — intentional for dev/test use.
 */
@ApplicationScoped
@JBossLog
public class EmulatorStore {

    private final AtomicInteger idSeq = new AtomicInteger(1000);

    // ── Auth ─────────────────────────────────────────────────────────────────
    /** Valid API keys → uid */
    private final Map<String, Integer> apiKeys = new ConcurrentHashMap<>(Map.of(
            "emulator-api-key-dev-only", 1
    ));

    // ── res.partner ───────────────────────────────────────────────────────────
    private final Map<Integer, OdooPartner> partners = new ConcurrentHashMap<>();

    // ── account.move (invoices) ───────────────────────────────────────────────
    private final Map<Integer, OdooInvoice> invoices = new ConcurrentHashMap<>();

    // ── payment.transaction ───────────────────────────────────────────────────
    private final Map<Integer, OdooPaymentTransaction> transactions = new ConcurrentHashMap<>();

    // ── ir.attachment (documents) ─────────────────────────────────────────────
    private final Map<Integer, OdooAttachment> attachments = new ConcurrentHashMap<>();

    public EmulatorStore() {
        seedData();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public boolean isValidApiKey(String key) {
        return key != null && apiKeys.containsKey(key);
    }

    public int registerApiKey(String key) {
        int uid = idSeq.getAndIncrement();
        apiKeys.put(key, uid);
        return uid;
    }

    public Optional<Integer> getUidForKey(String key) {
        return Optional.ofNullable(apiKeys.get(key));
    }

    // ── res.partner ───────────────────────────────────────────────────────────

    public List<OdooPartner> searchPartners(String domain) {
        return new ArrayList<>(partners.values());
    }

    public Optional<OdooPartner> getPartner(int id) {
        return Optional.ofNullable(partners.get(id));
    }

    public OdooPartner createPartner(OdooPartner p) {
        p.setId(idSeq.getAndIncrement());
        partners.put(p.getId(), p);
        log.infof("Partner created: id=%d name=%s", p.getId(), p.getName());
        return p;
    }

    public Optional<OdooPartner> updatePartner(int id, OdooPartner patch) {
        return Optional.ofNullable(partners.computeIfPresent(id, (k, existing) -> {
            if (patch.getName() != null)  existing.setName(patch.getName());
            if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
            if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
            return existing;
        }));
    }

    // ── account.move ──────────────────────────────────────────────────────────

    public List<OdooInvoice> searchInvoices(Integer partnerId) {
        return invoices.values().stream()
                .filter(i -> partnerId == null || partnerId.equals(i.getPartnerId()))
                .toList();
    }

    public Optional<OdooInvoice> getInvoice(int id) {
        return Optional.ofNullable(invoices.get(id));
    }

    public OdooInvoice createInvoice(OdooInvoice inv) {
        inv.setId(idSeq.getAndIncrement());
        inv.setState("draft");
        inv.setCreateDate(LocalDateTime.now().toString());
        invoices.put(inv.getId(), inv);
        log.infof("Invoice created: id=%d partner=%d amount=%.2f", inv.getId(), inv.getPartnerId(), inv.getAmountTotal());
        return inv;
    }

    public boolean confirmInvoice(int id) {
        OdooInvoice inv = invoices.get(id);
        if (inv == null) return false;
        inv.setState("posted");
        log.infof("Invoice confirmed: id=%d", id);
        return true;
    }

    // ── payment.transaction ───────────────────────────────────────────────────

    public List<OdooPaymentTransaction> searchTransactions(Integer invoiceId) {
        return transactions.values().stream()
                .filter(t -> invoiceId == null || invoiceId.equals(t.getInvoiceId()))
                .toList();
    }

    public Optional<OdooPaymentTransaction> getTransaction(int id) {
        return Optional.ofNullable(transactions.get(id));
    }

    public OdooPaymentTransaction createTransaction(OdooPaymentTransaction tx) {
        tx.setId(idSeq.getAndIncrement());
        tx.setState("draft");
        tx.setCreateDate(LocalDateTime.now().toString());
        transactions.put(tx.getId(), tx);
        log.infof("Transaction created: id=%d invoice=%d amount=%.2f", tx.getId(), tx.getInvoiceId(), tx.getAmount());
        return tx;
    }

    public Optional<OdooPaymentTransaction> updateTransactionState(int id, String newState) {
        return Optional.ofNullable(transactions.computeIfPresent(id, (k, tx) -> {
            tx.setState(newState);
            tx.setLastUpdate(LocalDateTime.now().toString());
            log.infof("Transaction %d state → %s", id, newState);
            return tx;
        }));
    }

    // ── ir.attachment ─────────────────────────────────────────────────────────

    public OdooAttachment storeAttachment(OdooAttachment att) {
        att.setId(idSeq.getAndIncrement());
        att.setCreateDate(LocalDateTime.now().toString());
        attachments.put(att.getId(), att);
        log.infof("Attachment stored: id=%d name=%s resModel=%s resId=%d size=%d bytes",
                att.getId(), att.getName(), att.getResModel(), att.getResId(), att.getDatas().length);
        return att;
    }

    public Optional<OdooAttachment> getAttachment(int id) {
        return Optional.ofNullable(attachments.get(id));
    }

    public List<OdooAttachment> searchAttachments(String resModel, Integer resId) {
        return attachments.values().stream()
                .filter(a -> resModel == null || resModel.equals(a.getResModel()))
                .filter(a -> resId == null || resId.equals(a.getResId()))
                .map(a -> {
                    // Return metadata only (no binary data) for search results
                    OdooAttachment meta = new OdooAttachment();
                    meta.setId(a.getId());
                    meta.setName(a.getName());
                    meta.setResModel(a.getResModel());
                    meta.setResId(a.getResId());
                    meta.setMimetype(a.getMimetype());
                    meta.setCreateDate(a.getCreateDate());
                    meta.setDatas(new byte[0]);
                    return meta;
                })
                .toList();
    }

    public boolean deleteAttachment(int id) {
        return attachments.remove(id) != null;
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private void seedData() {
        // Seed partners
        OdooPartner p1 = new OdooPartner();
        p1.setId(1); p1.setName("Acme Recruiting GmbH");
        p1.setEmail("contact@acme-recruiting.de"); p1.setPhone("+49 30 123456");
        p1.setIsCompany(true); p1.setActive(true);
        partners.put(1, p1);

        OdooPartner p2 = new OdooPartner();
        p2.setId(2); p2.setName("Talent Solutions AG");
        p2.setEmail("info@talent-solutions.ch"); p2.setPhone("+41 44 987654");
        p2.setIsCompany(true); p2.setActive(true);
        partners.put(2, p2);

        // Seed invoice
        OdooInvoice inv = new OdooInvoice();
        inv.setId(101); inv.setPartnerId(1); inv.setAmountTotal(149.00);
        inv.setState("posted"); inv.setMoveType("out_invoice");
        inv.setRef("INV/2025/0001"); inv.setCreateDate("2025-01-15T10:00:00");
        invoices.put(101, inv);

        // Seed transaction
        OdooPaymentTransaction tx = new OdooPaymentTransaction();
        tx.setId(201); tx.setInvoiceId(101); tx.setAmount(149.00);
        tx.setState("done"); tx.setProviderCode("transfer");
        tx.setReference("PAY-2025-0001"); tx.setCreateDate("2025-01-15T10:05:00");
        tx.setLastUpdate("2025-01-15T10:10:00");
        transactions.put(201, tx);

        log.info("Odoo emulator store seeded with demo data.");
    }
}

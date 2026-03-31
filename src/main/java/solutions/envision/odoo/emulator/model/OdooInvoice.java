package solutions.envision.odoo.emulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OdooInvoice {
    private int id;
    @JsonProperty("partner_id")
    private int partnerId;
    @JsonProperty("move_type")
    private String moveType = "out_invoice";
    private String ref;
    private String state;           // draft | posted | cancel
    @JsonProperty("amount_total")
    private double amountTotal;
    @JsonProperty("amount_residual")
    private double amountResidual;
    @JsonProperty("currency_id")
    private String currencyId = "EUR";
    @JsonProperty("invoice_date")
    private String invoiceDate;
    @JsonProperty("invoice_date_due")
    private String invoiceDateDue;
    @JsonProperty("create_date")
    private String createDate;
    @JsonProperty("payment_state")
    private String paymentState = "not_paid"; // not_paid | partial | paid | reversed
    private String name;
}
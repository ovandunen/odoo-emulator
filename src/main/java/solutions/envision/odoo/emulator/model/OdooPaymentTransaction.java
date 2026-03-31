package solutions.envision.odoo.emulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OdooPaymentTransaction {
    private int id;
    @JsonProperty("invoice_id")
    private int invoiceId;
    private double amount;
    @JsonProperty("currency_id")
    private String currencyId = "EUR";
    private String state;           // draft | pending | authorized | done | cancel | error
    @JsonProperty("provider_code")
    private String providerCode;
    private String reference;
    @JsonProperty("partner_id")
    private int partnerId;
    @JsonProperty("create_date")
    private String createDate;
    @JsonProperty("last_state_change")
    private String lastUpdate;
    @JsonProperty("state_message")
    private String stateMessage;
}
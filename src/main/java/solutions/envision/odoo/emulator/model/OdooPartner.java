package solutions.envision.odoo.emulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OdooPartner {
    private int id;
    private String name;
    private String email;
    private String phone;
    private String street;
    private String city;
    private String zip;
    @JsonProperty("is_company")
    private boolean isCompany;
    private boolean active = true;
    private String comment;
}
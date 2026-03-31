package solutions.envision.odoo.emulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OdooAttachment {
    private int id;
    private String name;
    @JsonProperty("res_model")
    private String resModel;        // e.g. "account.move", "res.partner"
    @JsonProperty("res_id")
    private int resId;
    private String mimetype;
    @JsonProperty("create_date")
    private String createDate;
    /** Raw binary content — stored in memory, Base64 in JSON transport */
    private byte[] datas;
    @JsonProperty("file_size")
    private int fileSize;
    private String description;
}
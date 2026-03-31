package solutions.envision.odoo.emulator.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.jbosslog.JBossLog;
import solutions.envision.odoo.emulator.model.OdooAttachment;
import solutions.envision.odoo.emulator.service.EmulatorStore;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Convenience REST-style document endpoints (in addition to JSON-RPC ir.attachment).
 *
 * Upload:   POST /web/content/upload
 * Download: GET  /web/content/{id}
 * List:     GET  /web/content?res_model=account.move&res_id=101
 * Delete:   DELETE /web/content/{id}
 */
@Path("/web/content")
@JBossLog
public class DocumentResource {

    @Inject
    EmulatorStore store;

    /** Upload a document as multipart or base64 JSON */
    @POST
    @Path("/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(Map<String, Object> body) {
        String name      = str(body.get("name"));
        String resModel  = str(body.get("res_model"));
        int    resId     = intVal(body.get("res_id"));
        String mimetype  = str(body.getOrDefault("mimetype", "application/octet-stream"));
        String datasB64  = str(body.get("datas"));
        String desc      = str(body.get("description"));

        if (name == null || datasB64 == null) {
            return Response.status(400).entity(Map.of("error", "name and datas (base64) are required")).build();
        }

        byte[] raw = Base64.getDecoder().decode(datasB64);

        OdooAttachment att = new OdooAttachment();
        att.setName(name);
        att.setResModel(resModel);
        att.setResId(resId);
        att.setMimetype(mimetype);
        att.setDescription(desc);
        att.setDatas(raw);
        att.setFileSize(raw.length);

        OdooAttachment saved = store.storeAttachment(att);
        log.infof("Document uploaded: id=%d name=%s size=%d", saved.getId(), saved.getName(), saved.getFileSize());

        return Response.ok(Map.of(
                "id",          saved.getId(),
                "name",        saved.getName(),
                "res_model",   saved.getResModel() != null ? saved.getResModel() : "",
                "res_id",      saved.getResId(),
                "file_size",   saved.getFileSize(),
                "create_date", saved.getCreateDate()
        )).build();
    }

    /** Download the raw binary content of an attachment */
    @GET
    @Path("/{id}")
    public Response download(@PathParam("id") int id,
                             @QueryParam("download") @DefaultValue("false") boolean forceDownload) {
        return store.getAttachment(id).map(att -> {
            log.infof("Document downloaded: id=%d name=%s", id, att.getName());
            Response.ResponseBuilder rb = Response.ok(att.getDatas())
                    .type(att.getMimetype() != null ? att.getMimetype() : "application/octet-stream")
                    .header("X-Odoo-Attachment-Id", att.getId())
                    .header("X-Odoo-Filename", att.getName());
            if (forceDownload) {
                rb.header("Content-Disposition", "attachment; filename=\"" + att.getName() + "\"");
            }
            return rb.build();
        }).orElse(Response.status(404).entity(Map.of("error", "Attachment not found: " + id)).build());
    }

    /** Download as base64 JSON (useful when your client expects Odoo's datas field) */
    @GET
    @Path("/{id}/base64")
    @Produces(MediaType.APPLICATION_JSON)
    public Response downloadBase64(@PathParam("id") int id) {
        return store.getAttachment(id).map(att -> Response.ok(Map.of(
                "id",          att.getId(),
                "name",        att.getName(),
                "mimetype",    att.getMimetype() != null ? att.getMimetype() : "",
                "file_size",   att.getFileSize(),
                "datas",       Base64.getEncoder().encodeToString(att.getDatas()),
                "create_date", att.getCreateDate()
        )).build()).orElse(Response.status(404).entity(Map.of("error", "Attachment not found: " + id)).build());
    }

    /** List attachments for a given model/record */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("res_model") String resModel,
                         @QueryParam("res_id")    Integer resId) {
        List<OdooAttachment> results = store.searchAttachments(resModel, resId);
        log.debugf("Attachment search: res_model=%s res_id=%s → %d results", resModel, resId, results.size());
        return Response.ok(results.stream().map(a -> Map.of(
                "id",          a.getId(),
                "name",        a.getName(),
                "res_model",   a.getResModel() != null ? a.getResModel() : "",
                "res_id",      a.getResId(),
                "mimetype",    a.getMimetype() != null ? a.getMimetype() : "",
                "file_size",   a.getFileSize(),
                "create_date", a.getCreateDate()
        )).toList()).build();
    }

    /** Delete an attachment */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") int id) {
        boolean deleted = store.deleteAttachment(id);
        if (deleted) {
            log.infof("Attachment deleted: id=%d", id);
            return Response.ok(Map.of("result", true)).build();
        }
        return Response.status(404).entity(Map.of("error", "Attachment not found: " + id)).build();
    }

    private String str(Object o)  { return o != null ? o.toString() : null; }
    private int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) return Integer.parseInt(s);
        return 0;
    }
}

package solutions.envision.odoo.emulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fires outbound HTTP POST to your Quarkus app, simulating Odoo calling back.
 */
@ApplicationScoped
@JBossLog
public class WebhookDispatchService {

    @ConfigProperty(name = "odoo.emulator.target-webhook-url",
            defaultValue = "http://localhost:8080/api/odoo/webhook")
    String defaultWebhookUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public String getDefaultWebhookUrl() {
        return defaultWebhookUrl;
    }

    /**
     * Fires the webhook asynchronously so the emulator doesn't block waiting for your app.
     */
    public void dispatch(String targetUrl, Map<String, Object> payload) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = mapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-Odoo-Webhook", "emulator")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.infof("Webhook dispatched → %s | status=%d | response=%s",
                        targetUrl, response.statusCode(), response.body());

            } catch (Exception e) {
                log.warnf("Webhook dispatch failed → %s : %s", targetUrl, e.getMessage());
            }
        });
    }
}
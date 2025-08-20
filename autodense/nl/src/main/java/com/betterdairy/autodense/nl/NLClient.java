package com.betterdairy.autodense.nl;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class NLClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final URI endpoint;

    public NLClient(URI endpoint) {
        this.endpoint = endpoint;
    }

    public JSONObject plan(String prompt, NLContext ctx) {
        try {
            JSONObject body = new JSONObject()
                .put("prompt", prompt)
                .put("context", ctx.metadata());
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JSONObject plan = new JSONObject(resp.body());
            JsonSchemas.validate(plan);
            return plan;
        } catch (Exception e) {
            throw new RuntimeException("NL planning failed", e);
        }
    }
}

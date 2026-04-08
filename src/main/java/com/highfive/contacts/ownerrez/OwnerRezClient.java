package com.highfive.contacts.ownerrez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.highfive.contacts.model.MailchimpContact;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OwnerRez v2 API client.
 * Supports: check if guest exists by email, create a new guest.
 * Auth: HTTP Basic Auth (email:personalAccessToken).
 */
public class OwnerRezClient {

    private static final String BASE_URL = "https://api.ownerrez.com/v2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String authHeader;

    public OwnerRezClient(String email, String token) {
        String credentials = email + ":" + token;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns true if a guest with this exact email already exists in OwnerRez.
     */
    public boolean existsByEmail(String email) throws IOException {
        String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
        JsonNode resp = get("/guests?q=" + encoded);
        JsonNode items = resp.path("items");
        if (!items.isArray()) return false;

        for (JsonNode guest : items) {
            for (JsonNode e : guest.path("email_addresses")) {
                if (e.path("address").asText("").equalsIgnoreCase(email)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a new guest in OwnerRez.
     * Returns the created guest's numeric ID.
     */
    public int createGuest(MailchimpContact contact) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        if (!contact.getFirstName().isEmpty()) body.put("first_name", contact.getFirstName());
        if (!contact.getLastName().isEmpty())  body.put("last_name",  contact.getLastName());

        ArrayNode emails = body.putArray("email_addresses");
        ObjectNode emailEntry = emails.addObject();
        emailEntry.put("address", contact.getEmail());
        emailEntry.put("is_default", true);

        if (!contact.getPhone().isEmpty()) {
            ArrayNode phones = body.putArray("phone_numbers");
            ObjectNode phoneEntry = phones.addObject();
            phoneEntry.put("number", contact.getPhone());
            phoneEntry.put("kind", "mobile");
        }

        String json = MAPPER.writeValueAsString(body);
        JsonNode resp = post("/guests", json);
        int id = resp.path("id").asInt(0);
        if (id == 0) {
            throw new IOException("OwnerRez returned no guest ID after creation. Response: "
                    + resp.toString().substring(0, Math.min(300, resp.toString().length())));
        }
        return id;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private JsonNode get(String path) throws IOException {
        String url = BASE_URL + path;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(req);
    }

    private JsonNode post(String path, String jsonBody) throws IOException {
        String url = BASE_URL + path;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    private JsonNode send(HttpRequest req) throws IOException {
        int attempts = 0;
        while (true) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 429) {
                    attempts++;
                    if (attempts >= 5) throw new IOException("OwnerRez rate limit exceeded after " + attempts + " retries");
                    long waitMs = (long) Math.pow(2, attempts) * 1000L;
                    System.out.println("[OwnerRez] Rate limited — waiting " + (waitMs / 1000) + "s...");
                    Thread.sleep(waitMs);
                    continue;
                }
                if (resp.statusCode() == 401) {
                    throw new IOException("OwnerRez authentication failed (401) — check ownerrez.email and ownerrez.token in config.properties");
                }
                if (resp.statusCode() >= 400) {
                    throw new IOException("OwnerRez API error " + resp.statusCode() + ": "
                            + resp.body().substring(0, Math.min(300, resp.body().length())));
                }
                return MAPPER.readTree(resp.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OwnerRez request interrupted", e);
            }
        }
    }
}

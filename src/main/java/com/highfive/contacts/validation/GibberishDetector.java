package com.highfive.contacts.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects gibberish email local parts using the HuggingFace Inference API.
 *
 * Model: madhurjindal/autonlp-Gibberish-Detector-492513457
 * Labels: clean | mild gibberish | word salad | noise
 *
 * Only the local part (before @) is sent to the model — the domain would bias results.
 * Local parts of 3 chars or fewer are skipped (too short for reliable classification).
 */
public class GibberishDetector {

    private static final String API_URL_TEMPLATE =
            "https://router.huggingface.co/hf-inference/models/%s";
    private static final double GIBBERISH_THRESHOLD = 0.85;
    private static final int MAX_RETRIES = 2;
    private static final long RATE_LIMIT_DELAY_MS = 120L;

    private final String apiUrl;
    private final String bearerToken;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache to avoid redundant API calls for duplicate local parts
    private final Map<String, Boolean> cache = new HashMap<>();

    private int callCount = 0;

    public GibberishDetector(String hfToken, String modelId) {
        this.bearerToken = hfToken;
        this.apiUrl = String.format(API_URL_TEMPLATE, modelId);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Returns true if the email local part is classified as gibberish.
     * Skips classification and returns false for short local parts (≤ 3 chars).
     */
    public boolean isGibberish(String email) throws IOException {
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return false;
        String localPart = email.substring(0, atIdx).toLowerCase();

        if (localPart.length() <= 3) return false;

        if (cache.containsKey(localPart)) return cache.get(localPart);

        // Rate limiting: pause every 50 calls
        if (callCount > 0 && callCount % 50 == 0) {
            try { Thread.sleep(RATE_LIMIT_DELAY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        boolean result = callHuggingFace(localPart);
        cache.put(localPart, result);
        callCount++;
        return result;
    }

    private boolean callHuggingFace(String text) throws IOException {
        String requestBody = "{\"inputs\": " + mapper.writeValueAsString(text) + "}";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 503) {
                    // Model is loading — wait and retry
                    System.out.println("[HuggingFace] Model loading (503) — waiting 10s before retry " + attempt + "...");
                    Thread.sleep(10_000);
                    continue;
                }
                if (resp.statusCode() != 200) {
                    throw new IOException("HuggingFace API error " + resp.statusCode() + ": "
                            + resp.body().substring(0, Math.min(200, resp.body().length())));
                }

                return parseGibberish(resp.body());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("HuggingFace request interrupted", e);
            }
        }
        throw new IOException("HuggingFace API failed after " + MAX_RETRIES + " retries for: " + text);
    }

    /**
     * Response format: [[{"label":"word salad","score":0.97},{"label":"clean","score":0.02},...]]
     * Returns true if the top label is "word salad" or "noise" with score > threshold.
     */
    private boolean parseGibberish(String json) throws IOException {
        JsonNode root = mapper.readTree(json);

        // The response is a 2D array: outer array has one element (the result for our single input)
        JsonNode predictions = root.isArray() && root.size() > 0 ? root.get(0) : root;

        if (!predictions.isArray() || predictions.size() == 0) {
            return false;
        }

        JsonNode top = predictions.get(0);
        String label = top.path("label").asText("").toLowerCase();
        double score = top.path("score").asDouble(0.0);

        return (label.equals("word salad") || label.equals("noise")) && score >= GIBBERISH_THRESHOLD;
    }
}

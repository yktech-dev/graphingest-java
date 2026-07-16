package io.graphingest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client for communicating with the GraphIngest platform.
 */
public class GraphIngestClient {

    private static final Gson GSON = new Gson();
    private static GraphIngestClient defaultInstance;

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public GraphIngestClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : getEnvWithFallback("GRAPHINGEST_API_URL", "INGEST_API_URL");
        this.apiKey = apiKey != null ? apiKey : getEnvWithFallback("GRAPHINGEST_API_KEY", "INGEST_API_KEY");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public GraphIngestClient() {
        this(null, null);
    }

    public static synchronized GraphIngestClient getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new GraphIngestClient();
        }
        return defaultInstance;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }

    /**
     * POST JSON to a platform endpoint and return parsed response.
     */
    public <T> T post(String path, Object body, Class<T> responseType) throws IOException, InterruptedException {
        String json = GSON.toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("GraphIngest API error " + response.statusCode() + ": " + response.body());
        }
        if (responseType == null || responseType == Void.class) {
            return null;
        }
        return GSON.fromJson(response.body(), responseType);
    }

    /**
     * POST JSON and return response as a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Object body) throws IOException, InterruptedException {
        String json = GSON.toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("GraphIngest API error " + response.statusCode() + ": " + response.body());
        }
        return GSON.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
    }

    private static String getEnvWithFallback(String primary, String fallback) {
        String val = System.getenv(primary);
        if (val == null || val.isEmpty()) {
            val = System.getenv(fallback);
        }
        return val != null ? val : "";
    }
}

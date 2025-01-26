package de.yanwittmann.document.ai;

import lombok.Builder;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;

@Builder
public class CompletionClient {
    private final String baseUrl;
    private final String model;
    private static final OkHttpClient httpClient;

    static {
        httpClient = new OkHttpClient.Builder()
                .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public JSONObject generateJsonCompletion(String prompt, double temperature) throws IOException {
        JSONObject payload = createBasePayload(prompt, temperature);
        payload.put("format", "json");

        JSONObject responseJson = executeRequest(payload);
        String innerResponse = responseJson.getString("response");
        return new JSONObject(innerResponse);
    }

    public String generateTextCompletion(String prompt, double temperature) throws IOException {
        JSONObject payload = createBasePayload(prompt, temperature);
        JSONObject responseJson = executeRequest(payload);
        return responseJson.getString("response");
    }

    public JSONObject generateJsonCompletion(String prompt) throws IOException {
        return generateJsonCompletion(prompt, 0.6);
    }

    public String generateTextCompletion(String prompt) throws IOException {
        return generateTextCompletion(prompt, 0.6);
    }

    private JSONObject createBasePayload(String prompt, double temperature) {
        final JSONObject payload = new JSONObject();
        payload.put("model", this.model);
        payload.put("prompt", prompt);
        payload.put("stream", false);
        payload.put("options", new JSONObject().put("temperature", temperature));

        return payload;
    }

    private JSONObject executeRequest(JSONObject payload) throws IOException {
        final RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json")
        );

        final Request request = new Request.Builder()
                .url(baseUrl + "/api/generate")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed: " + response);
            }

            String responseBody = response.body().string();
            return new JSONObject(responseBody);
        }
    }
}

package de.yanwittmann.document.ai;

import lombok.Builder;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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

    // Existing text completion methods
    public JSONObject generateJsonCompletion(String prompt, double temperature) throws IOException {
        JSONObject payload = createBasePayload(prompt, temperature);
        payload.put("format", "json");
        JSONObject responseJson = executeRequest(payload, "/api/generate");
        return new JSONObject(responseJson.getString("response"));
    }

    public String generateTextCompletion(String prompt, double temperature) throws IOException {
        JSONObject payload = createBasePayload(prompt, temperature);
        JSONObject responseJson = executeRequest(payload, "/api/generate");
        return responseJson.getString("response");
    }

    public JSONObject generateJsonCompletion(String prompt) throws IOException {
        return generateJsonCompletion(prompt, 0.6);
    }

    public String generateTextCompletion(String prompt) throws IOException {
        return generateTextCompletion(prompt, 0.6);
    }

    // New image completion methods
    public JSONObject generateImageJsonCompletion(String prompt, File imageFile, double temperature) throws IOException {
        return generateImageJsonCompletion(prompt, Collections.singletonList(imageFile), temperature);
    }

    public JSONObject generateImageJsonCompletion(String prompt, List<File> imageFiles, double temperature) throws IOException {
        JSONObject payload = createImagePayload(prompt, imageFiles, temperature);
        JSONObject responseJson = executeRequest(payload, "/api/chat");
        String content = responseJson.getJSONObject("message").getString("content");
        return new JSONObject(content);
    }

    public String generateImageTextCompletion(String prompt, File imageFile, double temperature) throws IOException {
        return generateImageTextCompletion(prompt, Collections.singletonList(imageFile), temperature);
    }

    public String generateImageTextCompletion(String prompt, List<File> imageFiles, double temperature) throws IOException {
        JSONObject payload = createImagePayload(prompt, imageFiles, temperature);
        JSONObject responseJson = executeRequest(payload, "/api/chat");
        return responseJson.getJSONObject("message").getString("content");
    }

    // Payload creation methods
    private JSONObject createBasePayload(String prompt, double temperature) {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);
        payload.put("options", new JSONObject().put("temperature", temperature));
        return payload;
    }

    private JSONObject createImagePayload(String prompt, List<File> imageFiles, double temperature) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("stream", false);
        payload.put("options", new JSONObject().put("temperature", temperature));

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        JSONArray images = new JSONArray();
        for (File imageFile : imageFiles) {
            byte[] imageBytes = FileUtils.readFileToByteArray(imageFile);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            images.put(base64Image);
        }
        userMessage.put("images", images);

        messages.put(userMessage);
        payload.put("messages", messages);

        return payload;
    }

    // Updated executeRequest to handle different endpoints
    private JSONObject executeRequest(JSONObject payload, String endpoint) throws IOException {
        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(baseUrl + endpoint)
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

package de.yanwittmann.document.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil {

    public static String fillTemplateFromFile(File filePath, Map<String, String> variables) throws IOException {
        String templateContent = readFile(filePath);
        return replacePlaceholders(templateContent, variables);
    }

    public static String fillTemplateFromClasspath(String filePath, Map<String, String> variables) throws IOException {
        String templateContent = readFile(filePath);
        return replacePlaceholders(templateContent, variables);
    }

    public static String readFile(File filePath) throws IOException {
        Path path = filePath.toPath();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static String readFile(String filePath) throws IOException {
        try (InputStream inputStream = ChatUtil.class.getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + filePath);
            }
            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
                return scanner.useDelimiter("\\A").next();
            }
        }
    }

    private static String replacePlaceholders(String template, Map<String, String> variables) {
        // placeholders like [[key]]
        final Pattern pattern = Pattern.compile("\\[\\[(.*?)]]");
        final Matcher matcher = pattern.matcher(template);
        final StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            final String key = matcher.group(1);
            final String replacement = variables.getOrDefault(key, "[[" + key + "]]");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static JSONObject extractJsonObject(String response) {
        Object result = extractJsonStructure(response);
        return (result instanceof JSONObject) ? (JSONObject) result : null;
    }

    public static JSONArray extractJsonArray(String response) {
        Object result = extractJsonStructure(response);
        return (result instanceof JSONArray) ? (JSONArray) result : null;
    }

    private static Object extractJsonStructure(String response) {
        // First try structured parsing for both objects and arrays
        Object result = structuredParse(response);
        if (result != null) return result;

        // Fallback to regex-based search with proper escaping
        return regexFallback(response);
    }

    private static Object structuredParse(String response) {
        int start = -1;
        int balance = 0;
        char expectedClosing = '\0';
        boolean inString = false;

        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);

            // Handle string literals
            if (c == '"' && (i == 0 || response.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (start == -1) {
                    if (c == '{' || c == '[') {
                        start = i;
                        expectedClosing = c == '{' ? '}' : ']';
                        balance = 1;
                    }
                } else {
                    if (c == expectedClosing) {
                        balance--;
                    } else if (c == (expectedClosing == '}' ? '{' : '[')) {
                        balance++;
                    }

                    if (balance == 0) {
                        String candidate = response.substring(start, i + 1);
                        try {
                            return parseCandidate(candidate, expectedClosing);
                        } catch (JSONException ex) {
                            start = -1;
                            balance = 0;
                            expectedClosing = '\0';
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Object regexFallback(String response) {
        // Fixed regex pattern with proper character class escaping
        Pattern pattern = Pattern.compile(
                "(\\{(?:[^{}\"]|\"(?:[^\"\\\\]|\\\\.)*\")*\\})" +  // Objects
                        "|" +
                        "(\\[(?:[^\\[\\]\"]|\"(?:[^\"\\\\]|\\\\.)*\")*\\])", // Arrays
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(response);
        while (matcher.find()) {
            String candidate = matcher.group();
            try {
                if (candidate.startsWith("{")) {
                    return new JSONObject(candidate);
                } else if (candidate.startsWith("[")) {
                    return new JSONArray(candidate);
                }
            } catch (JSONException ex) {
                // Continue searching
            }
        }
        return null;
    }

    private static Object parseCandidate(String candidate, char expectedClosing) throws JSONException {
        return expectedClosing == '}' ? new JSONObject(candidate) : new JSONArray(candidate);
    }

    public static void appendToFile(File file, String content) throws IOException {
        Files.write(file.toPath(), (content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    public static String filterThinking(String response) {
        final Pattern pattern = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
        return pattern.matcher(response).replaceAll("");
    }
}

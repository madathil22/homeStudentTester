package com.homestudenttester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {
    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String generateTestHtml(String subject) {
        String apiKey = properties.openAiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured. Set OPENAI_API_KEY in .env or environment.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Test subject is required.");
        }

        String prompt = buildUserPrompt(subject.trim());
        Map<String, Object> body = Map.of(
                "model", properties.openAiModel(),
                "temperature", properties.openAiTemperature(),
                "max_tokens", properties.openAiMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", prompt)));

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.openAiApiUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String html = extractHtml(json).trim();
            if (html.isBlank() || !(html.contains("<body") || html.contains("<html"))) {
                throw new IllegalStateException("OpenAI API returned an invalid test HTML payload.");
            }
            return html;
        } catch (IOException | InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to call OpenAI API.", error);
        }
    }

    private String buildSystemPrompt() {
        return "You are a reliable educational content specialist who writes credible curriculum-based assessment materials. "
                + "Generate only a complete HTML document containing a well-structured test. "
                + "Do not include analysis, debugging notes, or any explanation of your process. "
                + "Avoid trivial or nonsensical question formats such as arithmetic fill-in-the-blank items like 'What do you get by adding 200 to (blank)'. "
                + "Use realistic subject knowledge, clear instructions, and a professional test layout. "
                + "Limit the output to valid HTML only. "
                + "Include a title, directions, and a mix of question types appropriate for the subject.";
    }

    private String buildUserPrompt(String subject) {
        return "Create a full, ready-to-use test in HTML for the subject: '" + subject + "'. "
                + "The resulting page should be valid HTML with a title, instructions, and at least five questions. "
                + "Use multiple-choice and short answer questions as appropriate to the subject. "
                + "The HTML must contain no placeholders like '(blank)' and no obviously made-up or trivial questions. "
                + "Return only the final HTML document. "
                + "Do not append any markdown, JSON, or additional commentary.";
    }

    private String extractHtml(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no content.");
        }
        JsonNode message = choices.get(0).path("message");
        if (message.isMissingNode()) {
            throw new IllegalStateException("OpenAI API response is missing message content.");
        }
        return message.path("content").asText("");
    }
}

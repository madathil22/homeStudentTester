package com.homestudenttester.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.TokenUsage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

public final class ServiceUtils {
  private ServiceUtils() {
  }

  public static String extractContent(JsonNode root, Logger log) {
    if (root.path("status").asText("").equalsIgnoreCase("incomplete")) {
      String reason = root.path("incomplete_details").path("reason").asText("unknown");
      long reasoningTokens = root.path("usage").path("output_tokens_details").path("reasoning_tokens").asLong(-1);
      log.warn("OpenAI response incomplete: reason={}, reasoning_tokens={}", reason, reasoningTokens);
      throw new IllegalStateException("OpenAI response incomplete: " + reason
          + ". Increase OPENAI_MAX_OUTPUT_TOKENS or reduce OPENAI_REASONING_EFFORT.");
    }

    String content = extractFromResponsesOutput(root.path("output"));
    if (!content.isBlank()) {
      return content;
    }

    JsonNode outputText = root.path("output_text");
    if (!outputText.isMissingNode() && !outputText.asText(" ").isBlank()) {
      return outputText.asText("");
    }

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

  public static String extractFromResponsesOutput(JsonNode output) {
    if (output.isArray()) {
      for (JsonNode item : output) {
        String extracted = extractFromResponsesOutputItem(item);
        if (!extracted.isBlank()) {
          return extracted;
        }
      }
      return "";
    }
    if (output.isObject()) {
      return extractFromResponsesOutputItem(output);
    }
    return "";
  }

  public static String extractFromResponsesOutputItem(JsonNode item) {
    if (item.isMissingNode() || !item.isObject()) {
      return "";
    }
    JsonNode content = item.path("content");
    if (content.isArray()) {
      for (JsonNode element : content) {
        if (element.has("text")) {
          String text = element.path("text").asText("");
          if (!text.isBlank()) {
            return text;
          }
        }
        if ("output_text".equals(element.path("type").asText(null))) {
          String text = element.path("text").asText("");
          if (!text.isBlank()) {
            return text;
          }
        }
        JsonNode data = element.path("data");
        if (!data.isMissingNode() && !data.isNull()) {
          String json = data.toString();
          if (!json.isBlank()) {
            return json;
          }
        }
      }
    }
    JsonNode outputText = item.path("output_text");
    if (!outputText.isMissingNode() && !outputText.asText(" ").isBlank()) {
      return outputText.asText("");
    }
    return "";
  }

  public static String normalizeJsonContent(String content) {
    String trimmed = content
        .replaceFirst("(?is)^\\s*```(?:json)?\\s*", "")
        .replaceFirst("(?is)\\s*```\\s*$", "")
        .trim();
    int firstBrace = trimmed.indexOf('{');
    int lastBrace = trimmed.lastIndexOf('}');
    if (firstBrace >= 0 && lastBrace > firstBrace) {
      return trimmed.substring(firstBrace, lastBrace + 1);
    }
    return trimmed;
  }

  public static TokenUsage parseTokenUsage(JsonNode root) {
    JsonNode usage = root.path("usage");
    return new TokenUsage(
        usage.path("input_tokens").asLong(0),
        usage.path("input_tokens_details").path("cached_tokens").asLong(0),
        usage.path("output_tokens").asLong(0),
        usage.path("output_tokens_details").path("reasoning_tokens").asLong(0),
        usage.path("total_tokens").asLong(0));
  }

  public static void logTokenUsage(Logger log, String phase, TokenUsage usage) {
    log.info(
        "OpenAI {} token usage: input={}, cachedInput={}, output={}, reasoning={}, total={}",
        phase,
        usage.inputTokens(),
        usage.cachedInputTokens(),
        usage.outputTokens(),
        usage.reasoningTokens(),
        usage.totalTokens());
  }

  public static JsonNode firstPresent(ObjectMapper objectMapper, JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = node.path(fieldName);
      if (!value.isMissingNode() && !value.isNull()) {
        return value;
      }
    }
    return objectMapper.missingNode();
  }

  public static double numberValue(JsonNode node) {
    if (node.isNumber()) {
      return node.asDouble();
    }
    if (node.isTextual()) {
      try {
        return Double.parseDouble(node.asText().replaceAll("[^0-9.+-]", ""));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }

  public static String textValue(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return "";
    }
    if (node.isValueNode()) {
      return node.asText("");
    }
    return node.toString();
  }

  public static String normalizeQuestionType(String type) {
    if (type == null || type.isBlank()) {
      return "free_text";
    }
    String normalized = type.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    if (normalized.equals("short_answer") || normalized.equals("short_response") || normalized.equals("essay")
        || normalized.equals("text")) {
      return "free_text";
    }
    return normalized;
  }

  public static String sanitizeName(String value) {
    String sanitized = value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    return sanitized.isBlank() ? "answer" : sanitized;
  }

  public static String questionNumber(QuestionDto question, int index) {
    return isBlank(question.number()) ? String.valueOf(index + 1) : question.number().trim();
  }

  public static Set<String> normalizedLabels(List<String> labels) {
    Set<String> normalized = new HashSet<>();
    if (labels == null) {
      return normalized;
    }
    for (String label : labels) {
      if (!isBlank(label)) {
        normalized.add(normalizeLabel(label));
      }
    }
    return normalized;
  }

  public static List<String> normalizedCorrectionLabels(List<String> labels) {
    if (labels == null) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>();
    for (String label : labels) {
      if (!isBlank(label)) {
        normalized.add(normalizeLabel(label));
      }
    }
    normalized = new ArrayList<>(new HashSet<>(normalized));
    Collections.sort(normalized);
    return normalized;
  }

  public static void validateCorrectionLabels(QuestionDto question, String type, List<String> labels) {
    if (!type.equals("multiple_choice") && !type.equals("multi_select")) {
      return;
    }
    Set<String> optionLabels = normalizedOptionLabels(question.options());
    for (String label : labels) {
      if (!optionLabels.contains(normalizeLabel(label))) {
        throw new IllegalArgumentException("Correct option label is not present on the question: " + label + ".");
      }
    }
    if (type.equals("multiple_choice") && labels.size() != 1) {
      throw new IllegalArgumentException("Multiple-choice corrections require exactly one correct option label.");
    }
    if (type.equals("multi_select") && labels.size() < 2) {
      throw new IllegalArgumentException("Multi-select corrections require at least two correct option labels.");
    }
  }

  public static Set<String> normalizedOptionLabels(List<OptionDto> options) {
    Set<String> labels = new HashSet<>();
    if (options == null) {
      return labels;
    }
    for (OptionDto option : options) {
      if (option != null && !isBlank(option.label())) {
        labels.add(normalizeLabel(option.label()));
      }
    }
    return labels;
  }

  public static Set<String> submittedLabels(Object answer) {
    Set<String> labels = new HashSet<>();
    if (answer == null) {
      return labels;
    }
    if (answer instanceof Iterable<?> iterable) {
      for (Object value : iterable) {
        addSubmittedLabel(labels, value);
      }
      return labels;
    }
    if (answer.getClass().isArray()) {
      int length = java.lang.reflect.Array.getLength(answer);
      for (int index = 0; index < length; index++) {
        addSubmittedLabel(labels, java.lang.reflect.Array.get(answer, index));
      }
      return labels;
    }
    addSubmittedLabel(labels, answer);
    return labels;
  }

  public static String normalizeLabel(String label) {
    return label.trim().toUpperCase(Locale.ROOT);
  }

  public static String formatLabels(Set<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return "";
    }
    List<String> sortedLabels = new ArrayList<>(labels);
    sortedLabels.sort(String::compareTo);
    return String.join(", ", sortedLabels);
  }

  public static List<String> readStringList(ObjectMapper objectMapper, Logger log, String json) {
    if (isBlank(json)) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          json,
          objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (IOException error) {
      log.warn("Unable to read stored correction labels: {}", json, error);
      return List.of();
    }
  }

  public static String questionFingerprint(QuestionDto question) {
    StringBuilder value = new StringBuilder();
    value.append(normalizeForFingerprint(question == null ? "" : question.prompt()));
    value.append("|");
    if (question != null && question.options() != null) {
      question.options().stream()
          .map(ServiceUtils::optionFingerprint)
          .sorted()
          .forEach(option -> value.append(option).append("|"));
    }
    return fingerprint(value.toString());
  }

  public static String fingerprint(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(normalizeForFingerprint(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("Unable to compute correction fingerprint.", error);
    }
  }

  public static String normalizeForFingerprint(String value) {
    return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
  }

  public static Map<String, Object> answersByQuestionNumber(QuestionBank questionBank, Map<String, Object> answers) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (int index = 0; index < questionBank.questions().size(); index++) {
      QuestionDto question = questionBank.questions().get(index);
      String number = questionNumber(question, index);
      normalized.put(number, answers.get("question_" + sanitizeName(number)));
    }
    return normalized;
  }

  public static String formatPoints(double points) {
    String value = points == Math.rint(points)
        ? Long.toString(Math.round(points))
        : Double.toString(points);
    return value + (points == 1 ? " point" : " points");
  }

  public static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public static String indentContent(String content) {
    return "          " + content.replace("\n", "\n          ");
  }

  public static String escapeAttribute(String value) {
    return escapeHtml(value == null ? "" : value);
  }

  public static String escapeMultilineText(String value) {
    return escapeHtml(value == null ? "" : value).replace("\n", "<br>");
  }

  public static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static void addSubmittedLabel(Set<String> labels, Object value) {
    if (value != null && !value.toString().isBlank()) {
      labels.add(normalizeLabel(value.toString()));
    }
  }

  private static String optionFingerprint(OptionDto option) {
    if (option == null) {
      return "";
    }
    return normalizeForFingerprint(option.label()) + "=" + normalizeForFingerprint(option.text());
  }
}

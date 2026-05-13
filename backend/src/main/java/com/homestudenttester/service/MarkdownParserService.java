package com.homestudenttester.service;

import com.homestudenttester.dto.AnswerInfo;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.PassageDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MarkdownParserService {
  private static final Pattern QUESTION_HEADING = Pattern.compile("^##\\s+Question\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PASSAGE_HEADING = Pattern.compile("^##\\s+Passage:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPTION_LINE = Pattern.compile("^([A-Z])\\.\\s+(.+)$");
  private static final Pattern META_LINE = Pattern.compile("^([A-Za-z][A-Za-z ]*):\\s*(.*)$");
  private static final Pattern TITLE_LINE = Pattern.compile("^#\\s+(.+)$");
  private static final Pattern ANSWER_BANK_HEADING = Pattern.compile("^#\\s+Answer Bank\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Set<String> SUPPORTED_TYPES = Set.of(
      "multiple_choice",
      "multi_select",
      "short_response",
      "essay",
      "text");

  public QuestionBank parseQuestionBank(String markdown) {
    List<String> lines = normalize(markdown);
    String title = lines.stream()
        .map(TITLE_LINE::matcher)
        .filter(Matcher::matches)
        .map(match -> match.group(1).trim())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Question bank must start with a # test title."));

    List<String> instructions = new ArrayList<>();
    List<MutablePassage> passages = new ArrayList<>();
    List<MutableQuestion> questions = new ArrayList<>();
    MutablePassage currentPassage = null;
    MutableQuestion currentQuestion = null;

    for (String line : lines) {
      Matcher questionMatch = QUESTION_HEADING.matcher(line);
      Matcher passageMatch = PASSAGE_HEADING.matcher(line);

      if (ANSWER_BANK_HEADING.matcher(line).matches()) {
        break;
      }
      if (isDivider(line) || TITLE_LINE.matcher(line).matches()) {
        continue;
      }

      if (passageMatch.matches()) {
        currentQuestion = null;
        currentPassage = new MutablePassage(
            String.valueOf(passages.size() + 1),
            passageMatch.group(1).trim());
        passages.add(currentPassage);
        continue;
      }

      if (questionMatch.matches()) {
        currentPassage = null;
        List<String> passageIds = passages.isEmpty()
            ? List.of()
            : List.of(passages.get(passages.size() - 1).id);
        currentQuestion = new MutableQuestion(questionMatch.group(1), passageIds);
        questions.add(currentQuestion);
        continue;
      }

      if (currentQuestion != null) {
        Matcher metaMatch = META_LINE.matcher(line);
        Matcher optionMatch = OPTION_LINE.matcher(line);

        if (metaMatch.matches() && List.of("type", "points").contains(metaMatch.group(1).toLowerCase())) {
          String key = metaMatch.group(1).toLowerCase();
          if (key.equals("type")) {
            currentQuestion.type = metaMatch.group(2).trim();
          }
          if (key.equals("points")) {
            currentQuestion.points = parsePoints(metaMatch.group(2), currentQuestion.number);
          }
          continue;
        }

        if (optionMatch.matches()) {
          currentQuestion.options.add(new OptionDto(optionMatch.group(1), optionMatch.group(2).trim()));
          continue;
        }

        currentQuestion.prompt = appendBlock(currentQuestion.prompt, line);
        continue;
      }

      if (currentPassage != null) {
        currentPassage.body = appendBlock(currentPassage.body, line);
        continue;
      }

      instructions.add(line);
    }

    List<QuestionDto> parsedQuestions = questions.stream()
        .map(question -> new QuestionDto(
            question.number,
            question.type.trim(),
            question.points,
            question.prompt.trim(),
            List.copyOf(question.options),
            List.copyOf(question.passageIds)))
        .toList();

    validateQuestionBank(parsedQuestions);

    return new QuestionBank(
        title,
        String.join("\n", instructions).trim(),
        passages.stream()
            .map(passage -> new PassageDto(passage.id, passage.title, passage.body.trim()))
            .toList(),
        parsedQuestions);
  }

  public Map<String, AnswerInfo> parseAnswerBank(String markdown) {
    List<String> lines = normalize(markdown);
    Map<String, AnswerInfoBuilder> answers = new LinkedHashMap<>();
    AnswerInfoBuilder current = null;
    String collecting = null;
    boolean inAnswerBank = lines.stream().noneMatch(line -> ANSWER_BANK_HEADING.matcher(line).matches());

    for (String line : lines) {
      if (ANSWER_BANK_HEADING.matcher(line).matches()) {
        inAnswerBank = true;
        current = null;
        collecting = null;
        continue;
      }

      if (!inAnswerBank || isDivider(line)) {
        continue;
      }

      Matcher questionMatch = QUESTION_HEADING.matcher(line);
      if (questionMatch.matches()) {
        current = new AnswerInfoBuilder(questionMatch.group(1));
        answers.put(current.number, current);
        collecting = null;
        continue;
      }

      if (current == null || TITLE_LINE.matcher(line).matches()) {
        continue;
      }

      Matcher metaMatch = META_LINE.matcher(line);
      if (metaMatch.matches()) {
        String key = metaMatch.group(1).toLowerCase();
        String value = metaMatch.group(2).trim();

        if (key.equals("answer")) {
          current.answer = value;
          collecting = null;
          continue;
        }
        if (key.equals("accept")) {
          current.accepted = splitAnswers(value);
          collecting = null;
          continue;
        }
        if (key.equals("points")) {
          current.points = parsePoints(value, current.number);
          collecting = null;
          continue;
        }
        if (key.equals("explanation")) {
          current.explanation = value;
          collecting = "explanation";
          continue;
        }
        if (key.equals("rubric")) {
          current.rubric = value;
          collecting = "rubric";
          continue;
        }
        if (key.equals("sample answer")) {
          current.sampleAnswer = value;
          collecting = "sampleAnswer";
          continue;
        }
      }

      if (collecting != null) {
        current.append(collecting, line);
      }
    }

    Map<String, AnswerInfo> parsed = new LinkedHashMap<>();
    answers.forEach((key, answer) -> parsed.put(key, answer.build()));
    return parsed;
  }

  public List<String> splitAnswers(String value) {
    return Pattern.compile(",")
        .splitAsStream(String.valueOf(value))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
  }

  private List<String> normalize(String markdown) {
    return List.of((markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1));
  }

  private String appendBlock(String existing, String line) {
    if (existing.isBlank() && line.trim().isEmpty()) {
      return "";
    }
    return (existing + (existing.isBlank() ? "" : "\n") + line).replaceFirst("\\s+$", "");
  }

  private boolean isDivider(String line) {
    return line.trim().matches("^-{3,}\\s*$");
  }

  private double parsePoints(String value, String questionNumber) {
    try {
      double points = Double.parseDouble(value);
      if (!Double.isFinite(points) || points < 0) {
        throw new NumberFormatException();
      }
      return points;
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Question " + questionNumber + " has invalid Points value.");
    }
  }

  private void validateQuestionBank(List<QuestionDto> questions) {
    if (questions.isEmpty()) {
      throw new IllegalArgumentException("Question bank must include at least one ## Question N section.");
    }

    for (QuestionDto question : questions) {
      if (question.type().isBlank()) {
        throw new IllegalArgumentException("Question " + question.number() + " is missing Type:.");
      }
      if (!SUPPORTED_TYPES.contains(question.type())) {
        throw new IllegalArgumentException("Question " + question.number() + " has unsupported Type: " + question.type() + ".");
      }
      if (question.prompt().isBlank()) {
        throw new IllegalArgumentException("Question " + question.number() + " is missing a prompt.");
      }
      if ((question.type().equals("multiple_choice") || question.type().equals("multi_select"))
          && question.options().size() < 2) {
        throw new IllegalArgumentException("Question " + question.number() + " needs at least two answer options.");
      }
    }
  }

  private static final class MutablePassage {
    private final String id;
    private final String title;
    private String body = "";

    private MutablePassage(String id, String title) {
      this.id = id;
      this.title = title;
    }
  }

  private static final class MutableQuestion {
    private final String number;
    private final List<String> passageIds;
    private String type = "";
    private double points = 1;
    private String prompt = "";
    private final List<OptionDto> options = new ArrayList<>();

    private MutableQuestion(String number, List<String> passageIds) {
      this.number = number;
      this.passageIds = passageIds;
    }
  }

  private final class AnswerInfoBuilder {
    private final String number;
    private String answer = "";
    private List<String> accepted = List.of();
    private Double points;
    private String explanation = "";
    private String rubric = "";
    private String sampleAnswer = "";

    private AnswerInfoBuilder(String number) {
      this.number = number;
    }

    private void append(String key, String line) {
      if (key.equals("explanation")) {
        explanation = appendBlock(explanation, line);
      }
      if (key.equals("rubric")) {
        rubric = appendBlock(rubric, line);
      }
      if (key.equals("sampleAnswer")) {
        sampleAnswer = appendBlock(sampleAnswer, line);
      }
    }

    private AnswerInfo build() {
      return new AnswerInfo(number, answer, accepted, points, explanation, rubric, sampleAnswer);
    }
  }
}

package ca.gc.tbs.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

/**
 * Scores person-name predictions against every labeled synthetic comment.
 *
 * <p>OpenNLP guesses are produced inside this program. Other models write guesses with {@code
 * scripts/predict_names.py}, then this program only scores them.
 *
 * <p>Run via {@code scripts/run-name-eval.sh opennlp} or {@code scripts/run-model-eval.sh gliner}.
 */
public class NameRedactionBaselineEval {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
  /** Drop OpenNLP person spans whose confidence is below this cutoff. */
  private static double openNlpConfidenceThreshold = 0.0;

  /** When true, skip synthetic-label size checks (real/unlabeled feedback samples). */
  private static boolean unlabeledMode = false;

  public static void main(String[] args) throws Exception {
    Path root = findProjectRoot();
    if (args.length > 0 && "--rebuild-index".equals(args[0])) {
      Path evalResultsDir = root.resolve("eval-results");
      writeIndexHtml(evalResultsDir);
      System.out.println("Rebuilt comparison index: " + evalResultsDir.resolve("index.html"));
      return;
    }

    List<String> positional = new ArrayList<>();
    Path dataFile = root.resolve("eval-data/synthetic_names_pi_test.ndjson");
    for (String arg : args) {
      if (arg.startsWith("--data=")) {
        dataFile = Path.of(arg.substring("--data=".length())).toAbsolutePath().normalize();
      } else if (arg.startsWith("--opennlp-threshold=")) {
        openNlpConfidenceThreshold =
            Double.parseDouble(arg.substring("--opennlp-threshold=".length()));
      } else if ("--unlabeled".equals(arg)) {
        unlabeledMode = true;
      } else if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      } else {
        positional.add(arg);
      }
    }

    String modelId = positional.isEmpty() ? "opennlp" : sanitizeModelId(positional.get(0));
    Path predictionsFile =
        positional.size() > 1
            ? Path.of(positional.get(1)).toAbsolutePath().normalize()
            : root.resolve("eval-data/predictions").resolve(modelId + ".jsonl");
    String timestamp = LocalDateTime.now().format(TS);
    Path outDir = root.resolve("eval-results").resolve(modelId).resolve(timestamp);
    Files.createDirectories(outDir);

    System.out.println("Project root: " + root);
    System.out.println("Reading labels from: " + dataFile);
    System.out.println("Writing results to: " + outDir);
    if (unlabeledMode) {
      System.out.println("Mode: unlabeled (no gold-name F1; prediction/redaction reports only)");
    }
    System.out.println();

    List<Comment> comments = loadComments(dataFile);
    TrustReport trust = verifyTrust(comments);
    printTrust(trust);
    if (!trust.ok) {
      throw new IllegalStateException(
          "Trust checks failed. Fix the labeled data before trusting any score.\n"
              + String.join("\n", trust.failures));
    }

    Map<String, Prediction> predictions;
    String guessSource;
    if ("opennlp".equals(modelId)) {
      System.out.println("Guess source: built-in OpenNLP models");
      System.out.println("OpenNLP confidence threshold: " + openNlpConfidenceThreshold);
      predictions = runOpenNlpPredictions(comments);
      guessSource =
          "Apache OpenNLP (en-token.bin + en-ner-person.bin), threshold="
              + openNlpConfidenceThreshold;
    } else {
      System.out.println("Guess source: " + predictionsFile);
      predictions = loadPredictions(predictionsFile, comments);
      guessSource = "External predictions file: " + predictionsFile.getFileName();
    }
    System.out.println();

    List<CommentResult> results = new ArrayList<>();
    int failures = 0;
    for (Comment comment : comments) {
      Prediction prediction = predictions.get(comment.id);
      if (prediction == null) {
        failures++;
        results.add(
            CommentResult.failed(
                comment, new IllegalStateException("Missing prediction for " + comment.id)));
        continue;
      }
      if (prediction.error != null && !prediction.error.isBlank()) {
        failures++;
        results.add(
            CommentResult.failed(comment, new IllegalStateException(prediction.error)));
        continue;
      }
      try {
        results.add(scorePredictedSpans(comment, prediction.spans));
      } catch (Exception e) {
        failures++;
        results.add(CommentResult.failed(comment, e));
      }
    }

    Metrics combined = Metrics.of(results, null, true);
    Metrics english = Metrics.of(results, "en", true);
    Metrics french = Metrics.of(results, "fr", true);
    Metrics policy = Metrics.of(results, null, false);

    if (!combined.reconciles() || !english.reconciles() || !french.reconciles()) {
      throw new IllegalStateException("Totals do not reconcile with per-comment results. Aborting.");
    }

    String summaryText =
        buildSummaryText(
            modelId, timestamp, guessSource, trust, english, french, combined, policy, failures);
    Files.writeString(outDir.resolve("summary.txt"), summaryText, StandardCharsets.UTF_8);
    writeSummaryCsv(outDir.resolve("summary.csv"), english, french, combined);
    writePerCommentCsv(outDir.resolve("per_comment.csv"), results);
    writeReportHtml(
        outDir.resolve("report.html"),
        modelId,
        timestamp,
        trust,
        english,
        french,
        combined,
        results,
        failures);
    Path evalResultsDir = root.resolve("eval-results");
    appendComparisonCsv(
        evalResultsDir.resolve("comparison.csv"),
        modelId,
        timestamp,
        english,
        french,
        combined,
        outDir);
    registerResultInActiveRun(evalResultsDir, modelId, timestamp);
    writeIndexHtml(evalResultsDir);

    System.out.println(summaryText);
    System.out.println();
    System.out.println("Evaluation finished.");
    System.out.println();
    System.out.println("WHERE TO LOOK:");
    System.out.println("  1. This Terminal window (summary printed above)");
    System.out.println("  2. Overview of every model: " + root.resolve("eval-results/index.html"));
    System.out.println("  3. This run's detailed report: " + outDir.resolve("report.html"));
    System.out.println("  4. Excel numbers: " + outDir.resolve("summary.csv"));
    System.out.println("  5. Every comment: " + outDir.resolve("per_comment.csv"));
  }

  private static String sanitizeModelId(String raw) {
    String cleaned = raw.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
    if (cleaned.isEmpty()) {
      throw new IllegalArgumentException("Model nickname cannot be empty.");
    }
    return cleaned;
  }

  private static Path findProjectRoot() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    if (Files.exists(cwd.resolve("eval-data/synthetic_names_pi_test.ndjson"))) {
      return cwd;
    }
    if (Files.exists(cwd.resolve("../eval-data/synthetic_names_pi_test.ndjson"))) {
      return cwd.resolve("..").normalize();
    }
    throw new IllegalStateException(
        "Could not find eval-data/synthetic_names_pi_test.ndjson. "
            + "Run scripts/run-name-eval.sh from the project root.");
  }

  private static TokenizerModel loadTokenizer() throws IOException {
    try (InputStream in =
        NameRedactionBaselineEval.class.getResourceAsStream("/opennlp/en-token.bin")) {
      if (in == null) {
        throw new IllegalStateException("Missing /opennlp/en-token.bin on the classpath.");
      }
      return new TokenizerModel(in);
    }
  }

  private static TokenNameFinderModel loadNer() throws IOException {
    try (InputStream in =
        NameRedactionBaselineEval.class.getResourceAsStream("/opennlp/en-ner-person.bin")) {
      if (in == null) {
        throw new IllegalStateException("Missing /opennlp/en-ner-person.bin on the classpath.");
      }
      return new TokenNameFinderModel(in);
    }
  }

  private static List<Comment> loadComments(Path dataFile) throws IOException {
    if (!Files.exists(dataFile)) {
      throw new IllegalStateException("Missing labeled data file: " + dataFile);
    }
    List<Comment> comments = new ArrayList<>();
    for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode root = MAPPER.readTree(line);
      JsonNode pi = root.path("piTest");
      List<CharSpan> gold = new ArrayList<>();
      for (JsonNode span : pi.path("spans")) {
        gold.add(
            new CharSpan(
                span.path("start").asInt(),
                span.path("end").asInt(),
                span.path("text").asText("")));
      }
      comments.add(
          new Comment(
              root.path("_id").asText(),
              root.path("problemDetails").asText(),
              root.path("language").asText(),
              pi.path("split").asText(""),
              pi.path("shouldRedact").asText(""),
              gold));
    }
    return comments;
  }

  private static TrustReport verifyTrust(List<Comment> comments) {
    TrustReport report = new TrustReport();
    report.total = comments.size();
    Set<String> ids = new HashSet<>();
    int dupes = 0;
    for (Comment c : comments) {
      if (!ids.add(c.id)) {
        dupes++;
      }
      report.splitCounts.merge(c.split.isEmpty() ? "(blank)" : c.split, 1, Integer::sum);
      report.languageCounts.merge(c.language, 1, Integer::sum);
      report.redactCounts.merge(c.shouldRedact, 1, Integer::sum);

      if ("yes".equals(c.shouldRedact) && c.gold.isEmpty()) {
        report.failures.add(c.id + ": shouldRedact=yes but gold spans are empty");
      }
      if ("no".equals(c.shouldRedact) && !c.gold.isEmpty()) {
        report.failures.add(c.id + ": shouldRedact=no but gold spans are not empty");
      }
      for (CharSpan span : c.gold) {
        if (span.start < 0 || span.end > c.text.length() || span.start > span.end) {
          report.failures.add(c.id + ": invalid gold span offsets " + span.start + "-" + span.end);
          continue;
        }
        String slice = c.text.substring(span.start, span.end);
        if (!slice.equals(span.text)) {
          report.failures.add(
              c.id
                  + ": gold span text mismatch. expected=["
                  + span.text
                  + "] actual=["
                  + slice
                  + "]");
        }
      }
    }
    report.uniqueIds = ids.size();
    report.duplicateIds = dupes;
    report.scoredForF1 =
        (int) comments.stream().filter(c -> !"policy_decision".equals(c.shouldRedact)).count();
    report.policyCount =
        (int) comments.stream().filter(c -> "policy_decision".equals(c.shouldRedact)).count();

    if (report.uniqueIds != report.total) {
      report.failures.add(
          "Duplicate IDs detected: unique=" + report.uniqueIds + " total=" + report.total);
    }
    if (report.duplicateIds != 0) {
      report.failures.add("Duplicate ID count=" + report.duplicateIds);
    }
    if (!unlabeledMode) {
      if (report.total != 1260) {
        report.failures.add("Expected 1260 comments, found " + report.total);
      }
      if (report.scoredForF1 != 1244) {
        report.failures.add("Expected 1244 non-policy comments, found " + report.scoredForF1);
      }
    }
    report.ok = report.failures.isEmpty();
    return report;
  }

  private static void printTrust(TrustReport trust) {
    System.out.println("TRUST CHECKS");
    System.out.println("  Total comments: " + trust.total);
    System.out.println(
        "  Unique IDs: " + trust.uniqueIds + " (duplicates: " + trust.duplicateIds + ")");
    System.out.println("  Languages: " + trust.languageCounts);
    System.out.println("  Splits (info only, NOT used to filter): " + trust.splitCounts);
    System.out.println("  shouldRedact: " + trust.redactCounts);
    System.out.println("  Scored in main F1: " + trust.scoredForF1);
    System.out.println("  Policy rows (shown separately): " + trust.policyCount);
    if (trust.ok) {
      System.out.println("  Result: PASSED");
    } else {
      System.out.println("  Result: FAILED");
      trust.failures.forEach(f -> System.out.println("   - " + f));
    }
    System.out.println();
  }

  private static Map<String, Prediction> runOpenNlpPredictions(List<Comment> comments)
      throws IOException {
    TokenizerModel tokenizerModel = loadTokenizer();
    TokenNameFinderModel nerModel = loadNer();
    TokenizerME tokenizer = new TokenizerME(tokenizerModel);
    NameFinderME nameFinder = new NameFinderME(nerModel);
    Map<String, Prediction> predictions = new LinkedHashMap<>();
    for (Comment comment : comments) {
      try {
        Span[] tokenSpans = tokenizer.tokenizePos(comment.text);
        String[] tokens = Span.spansToStrings(tokenSpans, comment.text);
        Span[] nameSpans = nameFinder.find(tokens);
        double[] probs = nameFinder.probs();
        List<CharSpan> predicted = new ArrayList<>();
        for (int i = 0; i < nameSpans.length; i++) {
          Span name = nameSpans[i];
          if (i < probs.length && probs[i] < openNlpConfidenceThreshold) {
            continue;
          }
          if (name.getStart() < 0
              || name.getEnd() > tokenSpans.length
              || name.getStart() >= name.getEnd()) {
            continue;
          }
          int charStart = tokenSpans[name.getStart()].getStart();
          int charEnd = tokenSpans[name.getEnd() - 1].getEnd();
          predicted.add(
              new CharSpan(charStart, charEnd, comment.text.substring(charStart, charEnd)));
        }
        predictions.put(comment.id, new Prediction(comment.id, predicted, null));
      } catch (Exception e) {
        predictions.put(
            comment.id,
            new Prediction(
                comment.id,
                List.of(),
                e.getClass().getSimpleName() + ": " + e.getMessage()));
      } finally {
        nameFinder.clearAdaptiveData();
      }
    }
    return predictions;
  }

  private static Map<String, Prediction> loadPredictions(Path predictionsFile, List<Comment> comments)
      throws IOException {
    if (!Files.exists(predictionsFile)) {
      throw new IllegalStateException(
          "Missing predictions file: "
              + predictionsFile
              + "\nRun: python3 scripts/predict_names.py <model>");
    }
    Map<String, Prediction> predictions = new LinkedHashMap<>();
    for (String line : Files.readAllLines(predictionsFile, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode root = MAPPER.readTree(line);
      String id = root.path("id").asText();
      if (id.isBlank()) {
        throw new IllegalStateException("Prediction line missing id: " + line);
      }
      List<CharSpan> spans = new ArrayList<>();
      for (JsonNode span : root.path("spans")) {
        int start = span.path("start").asInt();
        int end = span.path("end").asInt();
        String text = span.path("text").asText("");
        spans.add(new CharSpan(start, end, text));
      }
      String error =
          root.hasNonNull("error") && !root.get("error").asText("").isBlank()
              ? root.get("error").asText()
              : null;
      predictions.put(id, new Prediction(id, spans, error));
    }

    List<String> missing = new ArrayList<>();
    for (Comment comment : comments) {
      if (!predictions.containsKey(comment.id)) {
        missing.add(comment.id);
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Predictions file is missing "
              + missing.size()
              + " comment ids. First missing: "
              + missing.get(0));
    }
    return predictions;
  }

  private static CommentResult scorePredictedSpans(Comment comment, List<CharSpan> predictedRaw) {
    List<CharSpan> predicted = new ArrayList<>();
    for (CharSpan span : predictedRaw) {
      if (span.start < 0 || span.end > comment.text.length() || span.start > span.end) {
        throw new IllegalArgumentException(
            "Invalid predicted span offsets " + span.start + ":" + span.end);
      }
      String slice = comment.text.substring(span.start, span.end);
      predicted.add(new CharSpan(span.start, span.end, slice));
    }

    Set<String> goldKeys =
        comment.gold.stream().map(CharSpan::key).collect(Collectors.toCollection(HashSet::new));
    Set<String> predKeys =
        predicted.stream().map(CharSpan::key).collect(Collectors.toCollection(HashSet::new));

    List<CharSpan> truePositives = new ArrayList<>();
    List<CharSpan> falseNegatives = new ArrayList<>();
    List<CharSpan> falsePositives = new ArrayList<>();

    for (CharSpan gold : comment.gold) {
      if (predKeys.contains(gold.key())) {
        truePositives.add(gold);
      } else {
        falseNegatives.add(gold);
      }
    }
    for (CharSpan pred : predicted) {
      if (!goldKeys.contains(pred.key())) {
        falsePositives.add(pred);
      }
    }

    String errorType;
    if (!falseNegatives.isEmpty() && !falsePositives.isEmpty()) {
      errorType = "missed_name_and_wrong_guess";
    } else if (!falseNegatives.isEmpty()) {
      errorType = "missed_name";
    } else if (!falsePositives.isEmpty()) {
      errorType = "wrong_guess";
    } else {
      errorType = "perfect";
    }

    return new CommentResult(
        comment, predicted, truePositives, falsePositives, falseNegatives, errorType, null);
  }

  private static String buildSummaryText(
      String modelId,
      String timestamp,
      String guessSource,
      TrustReport trust,
      Metrics english,
      Metrics french,
      Metrics combined,
      Metrics policy,
      int failures) {
    StringBuilder sb = new StringBuilder();
    sb.append("NAME REDACTION EVALUATION SUMMARY\n");
    sb.append("=================================\n");
    sb.append("Model nickname: ").append(modelId).append('\n');
    sb.append("Run timestamp: ").append(timestamp).append('\n');
    sb.append("Guess source: ").append(guessSource).append('\n');
    sb.append('\n');
    sb.append("What was scored\n");
    sb.append("- All ").append(trust.total).append(" labeled comments (dev/test split IGNORED)\n");
    sb.append("- Main F1 uses ")
        .append(trust.scoredForF1)
        .append(" comments (excludes ")
        .append(trust.policyCount)
        .append(" policy_decision rows)\n");
    sb.append("- Matching rule: exact character start/end must match\n");
    sb.append("- Model prediction failures recorded (not dropped): ").append(failures).append('\n');
    sb.append('\n');
    sb.append(formatBucket("ENGLISH", english));
    sb.append('\n');
    sb.append(formatBucket("FRENCH", french));
    sb.append('\n');
    sb.append(formatBucket("COMBINED", combined));
    sb.append('\n');
    sb.append("POLICY_DECISION (not in main F1)\n");
    sb.append(
        String.format(
            Locale.ROOT,
            "  comments=%d  predicted_spans=%d  gold_spans=%d\n",
            policy.comments,
            policy.predictedSpans,
            policy.goldSpans));
    return sb.toString();
  }

  private static String formatBucket(String title, Metrics m) {
    return String.format(
        Locale.ROOT,
        "%s%n  comments=%d  gold_names=%d  predicted_names=%d%n"
            + "  true_positives=%d  false_positives=%d  false_negatives=%d%n"
            + "  precision=%.4f  recall=%.4f  F1=%.4f%n"
            + "  hard_negative_comments=%d  hard_negative_correct=%d  hard_negative_accuracy=%.4f%n",
        title,
        m.comments,
        m.goldSpans,
        m.predictedSpans,
        m.truePositives,
        m.falsePositives,
        m.falseNegatives,
        m.precision(),
        m.recall(),
        m.f1(),
        m.hardNegativeComments,
        m.hardNegativeCorrect,
        m.hardNegativeAccuracy());
  }

  private static void writeSummaryCsv(Path path, Metrics en, Metrics fr, Metrics all)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "language,comments,gold_names,predicted_names,true_positives,false_positives,false_negatives,precision,recall,f1,hard_negative_comments,hard_negative_correct,hard_negative_accuracy\n");
    sb.append(csvMetrics("english", en));
    sb.append(csvMetrics("french", fr));
    sb.append(csvMetrics("combined", all));
    Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
  }

  private static String csvMetrics(String language, Metrics m) {
    if (unlabeledMode) {
      // No gold labels: keep prediction counts, mark score columns as n/a for the index.
      return String.format(
          Locale.ROOT,
          "%s,%d,%d,%d,%d,%d,%d,n/a,n/a,n/a,%d,%d,n/a%n",
          language,
          m.comments,
          m.goldSpans,
          m.predictedSpans,
          m.truePositives,
          m.falsePositives,
          m.falseNegatives,
          m.hardNegativeComments,
          m.hardNegativeCorrect);
    }
    return String.format(
        Locale.ROOT,
        "%s,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%d,%d,%.6f%n",
        language,
        m.comments,
        m.goldSpans,
        m.predictedSpans,
        m.truePositives,
        m.falsePositives,
        m.falseNegatives,
        m.precision(),
        m.recall(),
        m.f1(),
        m.hardNegativeComments,
        m.hardNegativeCorrect,
        m.hardNegativeAccuracy());
  }

  private static void writePerCommentCsv(Path path, List<CommentResult> results) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "id,language,split,should_redact,error_type,in_main_f1,gold_spans,predicted_spans,true_positives,false_positives,false_negatives,gold_text,predicted_text,comment_text,redacted_text,failure\n");
    for (CommentResult r : results) {
      sb.append(csv(r.comment.id)).append(',');
      sb.append(csv(r.comment.language)).append(',');
      sb.append(csv(r.comment.split)).append(',');
      sb.append(csv(r.comment.shouldRedact)).append(',');
      sb.append(csv(r.errorType)).append(',');
      sb.append(!"policy_decision".equals(r.comment.shouldRedact)).append(',');
      sb.append(r.comment.gold.size()).append(',');
      sb.append(r.predicted.size()).append(',');
      sb.append(r.truePositives.size()).append(',');
      sb.append(r.falsePositives.size()).append(',');
      sb.append(r.falseNegatives.size()).append(',');
      sb.append(csv(joinSpans(r.comment.gold))).append(',');
      sb.append(csv(joinSpans(r.predicted))).append(',');
      sb.append(csv(r.comment.text)).append(',');
      sb.append(csv(redactPredictedSpans(r.comment.text, r.predicted))).append(',');
      sb.append(csv(r.failureMessage == null ? "" : r.failureMessage)).append('\n');
    }
    Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
  }

  private static String joinSpans(List<CharSpan> spans) {
    return spans.stream()
        .map(s -> s.text + " [" + s.start + ":" + s.end + "]")
        .collect(Collectors.joining(" | "));
  }

  /**
   * Replace each predicted name span with {@code #} characters (same style as production
   * {@code ContentService.cleanNames}).
   */
  private static String redactPredictedSpans(String text, List<CharSpan> predicted) {
    if (text == null) {
      return "";
    }
    if (predicted == null || predicted.isEmpty()) {
      return text;
    }
    List<CharSpan> ordered =
        predicted.stream()
            .filter(s -> s != null && s.start >= 0 && s.end <= text.length() && s.start < s.end)
            .sorted(Comparator.comparingInt((CharSpan s) -> s.start).reversed())
            .toList();
    StringBuilder sb = new StringBuilder(text);
    for (CharSpan span : ordered) {
      char[] replacement = new char[span.end - span.start];
      Arrays.fill(replacement, '#');
      sb.replace(span.start, span.end, new String(replacement));
    }
    return sb.toString();
  }

  private static String csv(String value) {
    String v = value == null ? "" : value;
    if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  private static void appendComparisonCsv(
      Path comparisonPath,
      String modelId,
      String timestamp,
      Metrics en,
      Metrics fr,
      Metrics all,
      Path outDir)
      throws IOException {
    Files.createDirectories(comparisonPath.getParent());
    String header =
        "model_id,timestamp,english_comments,english_precision,english_recall,english_f1,french_comments,french_precision,french_recall,french_f1,combined_comments,combined_precision,combined_recall,combined_f1,report_html\n";
    String newRow =
        String.format(
            Locale.ROOT,
            "%s,%s,%d,%.6f,%.6f,%.6f,%d,%.6f,%.6f,%.6f,%d,%.6f,%.6f,%.6f,%s%n",
            modelId,
            timestamp,
            en.comments,
            en.precision(),
            en.recall(),
            en.f1(),
            fr.comments,
            fr.precision(),
            fr.recall(),
            fr.f1(),
            all.comments,
            all.precision(),
            all.recall(),
            all.f1(),
            outDir.resolve("report.html"));

    if (!Files.exists(comparisonPath) || Files.size(comparisonPath) == 0) {
      Files.writeString(comparisonPath, header + newRow, StandardCharsets.UTF_8);
      return;
    }
    Files.writeString(
        comparisonPath, newRow, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
  }

  private static void registerResultInActiveRun(
      Path evalResultsDir, String modelId, String timestamp) throws IOException {
    Path runsPath = evalResultsDir.resolve("runs.json");
    ObjectNode root = loadOrCreateRunsDocument(runsPath);
    String activeDatasetId = textOr(root.get("activeDatasetId"), "synthetic-pi");
    String activeRunId = textOr(root.get("activeRunId"), "original");
    ObjectNode dataset = findOrCreateDataset(root, activeDatasetId);
    ObjectNode targetRun = null;
    for (JsonNode runNode : dataset.withArray("runs")) {
      if (runNode.isObject() && activeRunId.equals(textOr(runNode.get("id"), ""))) {
        targetRun = (ObjectNode) runNode;
        break;
      }
    }
    if (targetRun == null) {
      targetRun = dataset.withArray("runs").addObject();
      targetRun.put("id", activeRunId);
      targetRun.put("label", activeRunId);
      targetRun.put("notes", "");
      targetRun.putObject("thresholds");
      targetRun.putObject("results");
    }
    targetRun.with("results").put(modelId, timestamp);
    ObjectMapper pretty = MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);
    Files.writeString(runsPath, pretty.writeValueAsString(root) + "\n", StandardCharsets.UTF_8);
  }

  private static ObjectNode findOrCreateDataset(ObjectNode root, String datasetId) {
    for (JsonNode node : root.withArray("datasets")) {
      if (node.isObject() && datasetId.equals(textOr(node.get("id"), ""))) {
        return (ObjectNode) node;
      }
    }
    ObjectNode created = root.withArray("datasets").addObject();
    created.put("id", datasetId);
    created.put("label", datasetId);
    created.put("notes", "");
    created.putArray("runs");
    return created;
  }

  private static ObjectNode loadOrCreateRunsDocument(Path runsPath) throws IOException {
    if (Files.exists(runsPath) && Files.size(runsPath) > 0) {
      JsonNode parsed = MAPPER.readTree(Files.readString(runsPath, StandardCharsets.UTF_8));
      if (parsed != null && parsed.isObject()) {
        ObjectNode root = (ObjectNode) parsed;
        // Migrate older flat {runs:[...]} docs into a single synthetic-pi dataset.
        if ((!root.has("datasets") || !root.get("datasets").isArray())
            && root.has("runs")
            && root.get("runs").isArray()) {
          ObjectNode migrated = MAPPER.createObjectNode();
          migrated.put("activeDatasetId", "synthetic-pi");
          migrated.put("activeRunId", textOr(root.get("activeRunId"), "original"));
          ObjectNode dataset = migrated.putArray("datasets").addObject();
          dataset.put("id", "synthetic-pi");
          dataset.put("label", "Sample 1 — Synthetic PI test");
          dataset.put("notes", "Labeled synthetic name dataset.");
          dataset.set("runs", root.get("runs"));
          return migrated;
        }
        return root;
      }
    }
    ObjectNode root = MAPPER.createObjectNode();
    root.put("activeDatasetId", "synthetic-pi");
    root.put("activeRunId", "original");
    root.putArray("datasets");
    return root;
  }

  private static String textOr(JsonNode node, String fallback) {
    return node == null || node.isNull() ? fallback : node.asText(fallback);
  }

  private static void writeIndexHtml(Path evalResultsDir) throws IOException {
    Path runsPath = evalResultsDir.resolve("runs.json");
    ObjectNode runsDoc = loadOrCreateRunsDocument(runsPath);
    JsonNode datasets = runsDoc.get("datasets");
    if (datasets == null || !datasets.isArray() || datasets.isEmpty()) {
      Files.writeString(
          evalResultsDir.resolve("index.html"),
          """
          <!DOCTYPE html>
          <html lang="en"><head><meta charset="utf-8"/><title>Name redaction model comparison</title></head>
          <body><h1>Name redaction model comparison</h1><p>No datasets defined in runs.json yet.</p></body></html>
          """,
          StandardCharsets.UTF_8);
      return;
    }

    String activeDatasetId = textOr(runsDoc.get("activeDatasetId"), textOr(datasets.get(0).get("id"), "synthetic-pi"));
    String activeRunId = textOr(runsDoc.get("activeRunId"), "original");
    String defaultDatasetId = chooseDefaultDatasetId(datasets, activeDatasetId);

    StringBuilder datasetTabs = new StringBuilder();
    StringBuilder datasetPanels = new StringBuilder();
    for (JsonNode datasetNode : datasets) {
      if (!datasetNode.isObject()) {
        continue;
      }
      String datasetId = textOr(datasetNode.get("id"), "dataset");
      String datasetLabel = textOr(datasetNode.get("label"), datasetId);
      String datasetNotes = textOr(datasetNode.get("notes"), "");
      JsonNode runs = datasetNode.path("runs");
      boolean datasetSelected = datasetId.equals(defaultDatasetId);
      String defaultRunId =
          runs.isArray() && !runs.isEmpty()
              ? chooseDefaultTabId(runs, datasetSelected ? activeRunId : textOr(runs.get(0).get("id"), "run"))
              : "";

      datasetTabs
          .append("<button type=\"button\" class=\"dataset-tab")
          .append(datasetSelected ? " active" : "")
          .append("\" data-dataset=\"")
          .append(esc(datasetId))
          .append("\" role=\"tab\" aria-selected=\"")
          .append(datasetSelected)
          .append("\">")
          .append(esc(datasetLabel))
          .append("</button>\n");

      datasetPanels
          .append("<section class=\"dataset-panel")
          .append(datasetSelected ? " active" : "")
          .append("\" data-dataset-panel=\"")
          .append(esc(datasetId))
          .append("\">\n");
      if (!datasetNotes.isBlank()) {
        datasetPanels
            .append("<p class=\"note\">")
            .append(esc(datasetNotes))
            .append("</p>\n");
      }

      if (!runs.isArray() || runs.isEmpty()) {
        datasetPanels.append("<p class=\"muted\">No experiment runs yet for this sample.</p>\n");
      } else {
        StringBuilder runTabs = new StringBuilder();
        StringBuilder runPanels = new StringBuilder();
        for (JsonNode runNode : runs) {
          if (!runNode.isObject()) {
            continue;
          }
          String runId = textOr(runNode.get("id"), "run");
          String label = textOr(runNode.get("label"), runId);
          String notes = textOr(runNode.get("notes"), "");
          boolean selected = runId.equals(defaultRunId);
          String tabKey = datasetId + "::" + runId;
          runTabs
              .append("<button type=\"button\" class=\"tab")
              .append(selected ? " active" : "")
              .append("\" data-tab=\"")
              .append(esc(tabKey))
              .append("\" data-dataset=\"")
              .append(esc(datasetId))
              .append("\" role=\"tab\" aria-selected=\"")
              .append(selected)
              .append("\">")
              .append(esc(label))
              .append("</button>\n");
          runPanels
              .append("<section class=\"panel")
              .append(selected ? " active" : "")
              .append("\" data-panel=\"")
              .append(esc(tabKey))
              .append("\" data-dataset=\"")
              .append(esc(datasetId))
              .append("\" role=\"tabpanel\">\n");
          if (!notes.isBlank()) {
            runPanels.append("<p class=\"note\">").append(esc(notes)).append("</p>\n");
          }
          runPanels.append(buildThresholdListHtml(runNode.get("thresholds")));
          runPanels.append(
              buildRunResultsTableHtml(
                  evalResultsDir, runNode.get("results"), runNode.get("thresholds")));
          runPanels.append("</section>\n");
        }
        datasetPanels
            .append("<div class=\"tabs\" role=\"tablist\">\n")
            .append(runTabs)
            .append("</div>\n")
            .append(runPanels);
      }
      datasetPanels.append("</section>\n");
    }

    String html =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8"/>
          <title>Name redaction model comparison</title>
          <style>
            body { font-family: Georgia, serif; margin: 2rem; color: #1b1b1b; background: #f7f4ef; }
            h1 { margin-bottom: 0.25rem; }
            p { max-width: 52rem; }
            .dataset-tabs, .tabs { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 1.25rem 0 0.75rem; }
            .dataset-tab, .tab {
              font: inherit; cursor: pointer; background: #ebe4d8; border: 1px solid #c9beae;
              border-radius: 4px 4px 0 0; padding: 0.45rem 0.9rem; color: #1b1b1b;
            }
            .dataset-tab.active, .tab.active { background: white; border-bottom-color: white; font-weight: 700; }
            .dataset-tab { background: #d9cfc0; }
            .dataset-tab.active { background: #fff8e7; border-bottom-color: #fff8e7; }
            .dataset-panel { display: none; }
            .dataset-panel.active { display: block; }
            .panel {
              display: none; background: white; border: 1px solid #c9beae; padding: 1rem 1.1rem 1.25rem;
            }
            .panel.active { display: block; }
            table { border-collapse: collapse; width: 100%; background: white; }
            th, td { border: 1px solid #ccc; padding: 0.55rem 0.7rem; text-align: left; }
            th { background: #ebe4d8; }
            .note { background: #fff8e7; border-left: 4px solid #c48a00; padding: 0.8rem 1rem; }
            .thresholds { margin: 0.75rem 0 1rem; padding: 0; list-style: none; }
            .thresholds li { margin: 0.2rem 0; }
            .muted { color: #666; }
          </style>
        </head>
        <body>
          <h1>Name redaction model comparison</h1>
          <p>Pick a data sample first, then an experiment run. Only models scored in that run are listed.</p>
          <div class="dataset-tabs" role="tablist" aria-label="Data samples">
        """
            + datasetTabs
            + """
          </div>
        """
            + datasetPanels
            + """
          <script>
            document.querySelectorAll('.dataset-tab').forEach(function (tab) {
              tab.addEventListener('click', function () {
                var id = tab.getAttribute('data-dataset');
                document.querySelectorAll('.dataset-tab').forEach(function (t) {
                  t.classList.toggle('active', t === tab);
                  t.setAttribute('aria-selected', t === tab ? 'true' : 'false');
                });
                document.querySelectorAll('.dataset-panel').forEach(function (panel) {
                  panel.classList.toggle('active', panel.getAttribute('data-dataset-panel') === id);
                });
              });
            });
            document.querySelectorAll('.tab').forEach(function (tab) {
              tab.addEventListener('click', function () {
                var id = tab.getAttribute('data-tab');
                var dataset = tab.getAttribute('data-dataset');
                document.querySelectorAll('.tab[data-dataset=\"' + dataset + '\"]').forEach(function (t) {
                  t.classList.toggle('active', t === tab);
                  t.setAttribute('aria-selected', t === tab ? 'true' : 'false');
                });
                document.querySelectorAll('.panel[data-dataset=\"' + dataset + '\"]').forEach(function (panel) {
                  panel.classList.toggle('active', panel.getAttribute('data-panel') === id);
                });
              });
            });
          </script>
        </body>
        </html>
        """;
    Files.writeString(evalResultsDir.resolve("index.html"), html, StandardCharsets.UTF_8);
  }

  private static String chooseDefaultDatasetId(JsonNode datasets, String activeDatasetId) {
    for (JsonNode dataset : datasets) {
      if (dataset.isObject() && activeDatasetId.equals(textOr(dataset.get("id"), ""))) {
        return activeDatasetId;
      }
    }
    return textOr(datasets.get(0).get("id"), "synthetic-pi");
  }

  private static String chooseDefaultTabId(JsonNode runs, String activeRunId) {
    for (JsonNode runNode : runs) {
      if (runNode.isObject()
          && activeRunId.equals(textOr(runNode.get("id"), ""))
          && runNode.path("results").isObject()
          && runNode.path("results").size() > 0) {
        return activeRunId;
      }
    }
    for (JsonNode runNode : runs) {
      if (runNode.isObject()
          && runNode.path("results").isObject()
          && runNode.path("results").size() > 0) {
        return textOr(runNode.get("id"), "original");
      }
    }
    return textOr(runs.get(0).get("id"), "original");
  }

  private static String buildThresholdListHtml(JsonNode thresholds) {
    if (thresholds == null || !thresholds.isObject() || thresholds.size() == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder("<ul class=\"thresholds\">\n");
    thresholds
        .fields()
        .forEachRemaining(
            entry ->
                sb.append("<li><strong>")
                    .append(esc(entry.getKey()))
                    .append("</strong>: threshold ")
                    .append(esc(entry.getValue().asText()))
                    .append("</li>\n"));
    sb.append("</ul>\n");
    return sb.toString();
  }

  private static String buildRunResultsTableHtml(
      Path evalResultsDir, JsonNode results, JsonNode thresholds) throws IOException {
    if (results == null || !results.isObject() || results.size() == 0) {
      return "<p class=\"muted\">No results yet for this run.</p>\n";
    }
    List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
    results.fields().forEachRemaining(entries::add);
    entries.sort(Comparator.comparing(Map.Entry::getKey));

    // Detect unlabeled runs (F1 stored as n/a) and show prediction counts instead.
    boolean unlabeled = false;
    Map<String, Map<String, String>> metricsByModel = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : entries) {
      Path summaryCsv =
          evalResultsDir
              .resolve(entry.getKey())
              .resolve(entry.getValue().asText())
              .resolve("summary.csv");
      Map<String, String> metrics = readSummaryMetrics(summaryCsv);
      metricsByModel.put(entry.getKey(), metrics);
      String combinedF1 = metrics.getOrDefault("combined.f1", "");
      if ("n/a".equalsIgnoreCase(combinedF1)) {
        unlabeled = true;
      }
    }

    StringBuilder rows = new StringBuilder();
    for (Map.Entry<String, JsonNode> entry : entries) {
      String modelId = entry.getKey();
      String timestamp = entry.getValue().asText();
      Path reportHtml = evalResultsDir.resolve(modelId).resolve(timestamp).resolve("report.html");
      Map<String, String> metrics = metricsByModel.getOrDefault(modelId, Map.of());
      String threshold =
          thresholds != null && thresholds.has(modelId)
              ? thresholds.get(modelId).asText()
              : "n/a";
      String href = evalResultsDir.relativize(reportHtml).toString().replace('\\', '/');
      String en =
          unlabeled
              ? metrics.getOrDefault("english.predicted", "—")
              : metrics.getOrDefault("english.f1", "—");
      String fr =
          unlabeled
              ? metrics.getOrDefault("french.predicted", "—")
              : metrics.getOrDefault("french.f1", "—");
      String all =
          unlabeled
              ? metrics.getOrDefault("combined.predicted", "—")
              : metrics.getOrDefault("combined.f1", "—");
      rows.append("<tr>")
          .append("<td>")
          .append(esc(modelId))
          .append("</td>")
          .append("<td>")
          .append(esc(threshold))
          .append("</td>")
          .append("<td>")
          .append(esc(en))
          .append("</td>")
          .append("<td>")
          .append(esc(fr))
          .append("</td>")
          .append("<td>")
          .append(esc(all))
          .append("</td>")
          .append("<td><a href=\"")
          .append(esc(href))
          .append("\">Open report</a></td>")
          .append("</tr>\n");
    }

    String enHeader = unlabeled ? "EN names found" : "English F1";
    String frHeader = unlabeled ? "FR names found" : "French F1";
    String allHeader = unlabeled ? "Total names found" : "Combined F1";
    return """
        <table>
          <thead>
            <tr>
              <th>Model</th>
              <th>Threshold</th>
              <th>"""
        + esc(enHeader)
        + """
</th>
              <th>"""
        + esc(frHeader)
        + """
</th>
              <th>"""
        + esc(allHeader)
        + """
</th>
              <th>Detailed report</th>
            </tr>
          </thead>
          <tbody>
        """
        + rows
        + """
          </tbody>
        </table>
        """;
  }

  /** Reads F1 and predicted_names per language from summary.csv. */
  private static Map<String, String> readSummaryMetrics(Path summaryCsv) throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    if (!Files.exists(summaryCsv)) {
      return out;
    }
    List<String> lines = Files.readAllLines(summaryCsv, StandardCharsets.UTF_8);
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty()) {
        continue;
      }
      String[] parts = splitCsv(line);
      // language, comments, gold, predicted(3), ..., f1(9)
      if (parts.length > 9) {
        String lang = parts[0];
        out.put(lang + ".predicted", parts[3]);
        out.put(lang + ".f1", parts[9]);
      }
    }
    return out;
  }

  private static String[] splitCsv(String line) {
    List<String> parts = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          cur.append(ch);
        }
      } else if (ch == '"') {
        inQuotes = true;
      } else if (ch == ',') {
        parts.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    parts.add(cur.toString());
    return parts.toArray(String[]::new);
  }

  private static void writeReportHtml(
      Path path,
      String modelId,
      String timestamp,
      TrustReport trust,
      Metrics en,
      Metrics fr,
      Metrics all,
      List<CommentResult> results,
      int failures)
      throws IOException {
    StringBuilder rows = new StringBuilder();
    List<CommentResult> sorted =
        results.stream().sorted(Comparator.comparing((CommentResult r) -> r.comment.id)).toList();
    for (CommentResult r : sorted) {
      rows.append("<tr")
          .append(" data-lang=\"")
          .append(esc(r.comment.language))
          .append("\"")
          .append(" data-redact=\"")
          .append(esc(r.comment.shouldRedact))
          .append("\"")
          .append(" data-error=\"")
          .append(esc(r.errorType))
          .append("\">");
      rows.append("<td>").append(esc(r.comment.id)).append("</td>");
      rows.append("<td>").append(esc(r.comment.language)).append("</td>");
      rows.append("<td>").append(esc(r.comment.shouldRedact)).append("</td>");
      rows.append("<td>").append(esc(r.errorType)).append("</td>");
      rows.append("<td>").append(esc(joinSpans(r.comment.gold))).append("</td>");
      rows.append("<td>").append(esc(joinSpans(r.predicted))).append("</td>");
      rows.append("<td class=\"comment\">").append(esc(r.comment.text)).append("</td>");
      rows.append("<td class=\"comment\">")
          .append(esc(redactPredictedSpans(r.comment.text, r.predicted)))
          .append("</td>");
      rows.append("</tr>\n");
    }

    String html =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8"/>
          <title>Name redaction report</title>
          <style>
            :root { --bg:#f4efe6; --card:#fffdf8; --ink:#1d1a16; --line:#d7cdb8; }
            body { margin:0; font-family: "Iowan Old Style", "Palatino Linotype", Palatino, serif; color:var(--ink); background:
              radial-gradient(circle at top left, #f8f1df, transparent 40%),
              linear-gradient(180deg, #efe7d8, var(--bg)); }
            main { max-width: 1200px; margin: 0 auto; padding: 2rem 1.25rem 4rem; }
            h1,h2 { font-family: "Avenir Next", "Segoe UI", sans-serif; letter-spacing: -0.02em; }
            .cards { display:grid; grid-template-columns: repeat(auto-fit,minmax(260px,1fr)); gap:1rem; }
            .card { background:var(--card); border:1px solid var(--line); border-radius:14px; padding:1rem 1.1rem; box-shadow:0 8px 24px rgba(40,25,0,.05); }
            .card h3 { margin:0 0 .55rem; font-size:1.05rem; }
            .card .score { font-size:1.15rem; margin:.2rem 0; }
            .card .score strong { font-size:1.35rem; }
            .hint { color:#5d564b; font-size:.86rem; }
            .note, .trust, .counts { background:#fff8e8; border:1px solid #efd7a0; border-radius:12px; padding:1rem 1.1rem; margin:1.2rem 0; }
            .counts table { width:100%; border-collapse:collapse; background:transparent; }
            .counts th, .counts td { border-bottom:1px solid #efd7a0; padding:.45rem .4rem; text-align:left; }
            .filters { display:flex; flex-wrap:wrap; gap:.75rem; margin:1rem 0 1.25rem; align-items:end; }
            label { display:flex; flex-direction:column; gap:.3rem; font-size:.9rem; }
            select { min-width: 10rem; padding:.45rem .55rem; border-radius:8px; border:1px solid var(--line); background:white; }
            table { width:100%; border-collapse:collapse; background:var(--card); }
            th, td { border-bottom:1px solid var(--line); padding:.55rem .6rem; vertical-align:top; font-size:.92rem; }
            th { position:sticky; top:0; background:#efe6d4; text-align:left; }
            td.comment { max-width: 28rem; }
            .muted { color:#5d564b; }
            .count { margin:.5rem 0 1rem; }
            .back { display:inline-block; margin:0 0 1rem; padding:.45rem .8rem; border:1px solid var(--line); border-radius:8px; background:#fffdf8; color:var(--ink); text-decoration:none; font-family:"Avenir Next","Segoe UI",sans-serif; font-size:.95rem; }
            .back:hover { background:#efe6d4; }
          </style>
        </head>
        <body>
          <main>
            <a class="back" href="../../index.html">← Back to comparison</a>
            <h1>Name redaction evaluation</h1>
            <p class="muted">Model: <strong>__MODEL__</strong></p>

            <div class="counts">
              <h2>Names present vs detected</h2>
              <p class="hint">“Present” means names marked in the correct answers. “Detected” means names the model found in the exact right place.</p>
              <table>
                <thead>
                  <tr>
                    <th>Language</th>
                    <th>Comments scored</th>
                    <th>Names present</th>
                    <th>Names detected correctly</th>
                    <th>Model guesses (all)</th>
                    <th>Missed names</th>
                    <th>Wrong guesses</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>English</td>
                    <td>__EN_N__</td>
                    <td>__EN_GOLD__</td>
                    <td>__EN_TP__</td>
                    <td>__EN_PRED__</td>
                    <td>__EN_FN__</td>
                    <td>__EN_FP__</td>
                  </tr>
                  <tr>
                    <td>French</td>
                    <td>__FR_N__</td>
                    <td>__FR_GOLD__</td>
                    <td>__FR_TP__</td>
                    <td>__FR_PRED__</td>
                    <td>__FR_FN__</td>
                    <td>__FR_FP__</td>
                  </tr>
                  <tr>
                    <td>Combined</td>
                    <td>__ALL_N__</td>
                    <td>__ALL_GOLD__</td>
                    <td>__ALL_TP__</td>
                    <td>__ALL_PRED__</td>
                    <td>__ALL_FN__</td>
                    <td>__ALL_FP__</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="cards">
              <div class="card">
                <h3>English</h3>
                <div class="score">Precision: <strong>__EN_P__</strong></div>
                <div class="hint">(Of the names the model marked, what share were really names?)</div>
                <div class="score">Recall: <strong>__EN_R__</strong></div>
                <div class="hint">(Of the real names in the answers, what share did the model find?)</div>
                <div class="score">F1: <strong>__EN_F1__</strong></div>
                <div class="hint">(A single score that balances precision and recall.)</div>
              </div>
              <div class="card">
                <h3>French</h3>
                <div class="score">Precision: <strong>__FR_P__</strong></div>
                <div class="hint">(Of the names the model marked, what share were really names?)</div>
                <div class="score">Recall: <strong>__FR_R__</strong></div>
                <div class="hint">(Of the real names in the answers, what share did the model find?)</div>
                <div class="score">F1: <strong>__FR_F1__</strong></div>
                <div class="hint">(A single score that balances precision and recall.)</div>
              </div>
              <div class="card">
                <h3>Combined</h3>
                <div class="score">Precision: <strong>__ALL_P__</strong></div>
                <div class="hint">(Of the names the model marked, what share were really names?)</div>
                <div class="score">Recall: <strong>__ALL_R__</strong></div>
                <div class="hint">(Of the real names in the answers, what share did the model find?)</div>
                <div class="score">F1: <strong>__ALL_F1__</strong></div>
                <div class="hint">(A single score that balances precision and recall.)</div>
              </div>
              <div class="card">
                <h3>Hard negatives</h3>
                <div class="score"><strong>__HN__</strong></div>
                <div class="hint">Comments that should have no person name, where the model correctly marked none.</div>
              </div>
            </div>

            <div class="trust">
              <h2>Trust checks</h2>
              <ul>
                <li>Total comments: __TOTAL__</li>
                <li>Unique IDs: __UNIQUE__ (duplicates: __DUPES__)</li>
                <li>Main F1 comments (excludes policy): __SCORED__</li>
                <li>Policy rows shown separately: __POLICY__</li>
                <li>Prediction failures recorded: __FAILS__</li>
                <li>Languages: __LANGS__</li>
                <li>Splits (info only): __SPLITS__</li>
              </ul>
            </div>

            <h2>Every comment</h2>
            <div class="filters">
              <label>Language
                <select id="langFilter">
                  <option value="all">All</option>
                  <option value="en">English</option>
                  <option value="fr">French</option>
                </select>
              </label>
              <label>Redaction category
                <select id="redactFilter">
                  <option value="all">All</option>
                  <option value="yes">yes</option>
                  <option value="no">no</option>
                  <option value="policy_decision">policy_decision</option>
                </select>
              </label>
              <label>Error type
                <select id="errorFilter">
                  <option value="all">All</option>
                  <option value="perfect">perfect</option>
                  <option value="missed_name">missed_name</option>
                  <option value="wrong_guess">wrong_guess</option>
                  <option value="missed_name_and_wrong_guess">missed_name_and_wrong_guess</option>
                  <option value="model_failure">model_failure</option>
                </select>
              </label>
            </div>
            <p class="count" id="visibleCount"></p>
            <table>
              <thead>
                <tr>
                  <th>ID</th><th>Lang</th><th>shouldRedact</th><th>Error</th>
                  <th>Correct names</th><th>Model guesses</th><th>Comment</th><th>Redacted comment</th>
                </tr>
              </thead>
              <tbody id="rows">
                __ROWS__
              </tbody>
            </table>
          </main>
          <script>
            const langFilter = document.getElementById('langFilter');
            const redactFilter = document.getElementById('redactFilter');
            const errorFilter = document.getElementById('errorFilter');
            const rows = [...document.querySelectorAll('#rows tr')];
            const visibleCount = document.getElementById('visibleCount');
            function applyFilters() {
              let shown = 0;
              rows.forEach(row => {
                const okLang = langFilter.value === 'all' || row.dataset.lang === langFilter.value;
                const okRedact = redactFilter.value === 'all' || row.dataset.redact === redactFilter.value;
                const okError = errorFilter.value === 'all' || row.dataset.error === errorFilter.value;
                const show = okLang && okRedact && okError;
                row.style.display = show ? '' : 'none';
                if (show) shown++;
              });
              visibleCount.textContent = 'Showing ' + shown + ' of ' + rows.length + ' comments';
            }
            [langFilter, redactFilter, errorFilter].forEach(el => el.addEventListener('change', applyFilters));
            applyFilters();
          </script>
        </body>
        </html>
        """
            .replace("__MODEL__", esc(modelId))
            .replace("__EN_F1__", fmt(en.f1()))
            .replace("__EN_P__", fmt(en.precision()))
            .replace("__EN_R__", fmt(en.recall()))
            .replace("__EN_N__", Integer.toString(en.comments))
            .replace("__EN_GOLD__", Integer.toString(en.goldSpans))
            .replace("__EN_TP__", Integer.toString(en.truePositives))
            .replace("__EN_PRED__", Integer.toString(en.predictedSpans))
            .replace("__EN_FN__", Integer.toString(en.falseNegatives))
            .replace("__EN_FP__", Integer.toString(en.falsePositives))
            .replace("__FR_F1__", fmt(fr.f1()))
            .replace("__FR_P__", fmt(fr.precision()))
            .replace("__FR_R__", fmt(fr.recall()))
            .replace("__FR_N__", Integer.toString(fr.comments))
            .replace("__FR_GOLD__", Integer.toString(fr.goldSpans))
            .replace("__FR_TP__", Integer.toString(fr.truePositives))
            .replace("__FR_PRED__", Integer.toString(fr.predictedSpans))
            .replace("__FR_FN__", Integer.toString(fr.falseNegatives))
            .replace("__FR_FP__", Integer.toString(fr.falsePositives))
            .replace("__ALL_F1__", fmt(all.f1()))
            .replace("__ALL_P__", fmt(all.precision()))
            .replace("__ALL_R__", fmt(all.recall()))
            .replace("__ALL_N__", Integer.toString(all.comments))
            .replace("__ALL_GOLD__", Integer.toString(all.goldSpans))
            .replace("__ALL_TP__", Integer.toString(all.truePositives))
            .replace("__ALL_PRED__", Integer.toString(all.predictedSpans))
            .replace("__ALL_FN__", Integer.toString(all.falseNegatives))
            .replace("__ALL_FP__", Integer.toString(all.falsePositives))
            .replace("__HN__", all.hardNegativeCorrect + " / " + all.hardNegativeComments)
            .replace("__TOTAL__", Integer.toString(trust.total))
            .replace("__UNIQUE__", Integer.toString(trust.uniqueIds))
            .replace("__DUPES__", Integer.toString(trust.duplicateIds))
            .replace("__SCORED__", Integer.toString(trust.scoredForF1))
            .replace("__POLICY__", Integer.toString(trust.policyCount))
            .replace("__FAILS__", Integer.toString(failures))
            .replace("__LANGS__", esc(String.valueOf(trust.languageCounts)))
            .replace("__SPLITS__", esc(String.valueOf(trust.splitCounts)))
            .replace("__ROWS__", rows.toString());

    Files.writeString(path, html, StandardCharsets.UTF_8);
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  private static String esc(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private record CharSpan(int start, int end, String text) {
    String key() {
      return start + ":" + end;
    }
  }

  private record Comment(
      String id,
      String text,
      String language,
      String split,
      String shouldRedact,
      List<CharSpan> gold) {}

  private record Prediction(String id, List<CharSpan> spans, String error) {}

  private record CommentResult(
      Comment comment,
      List<CharSpan> predicted,
      List<CharSpan> truePositives,
      List<CharSpan> falsePositives,
      List<CharSpan> falseNegatives,
      String errorType,
      String failureMessage) {
    static CommentResult failed(Comment comment, Exception e) {
      return new CommentResult(
          comment,
          List.of(),
          List.of(),
          List.of(),
          new ArrayList<>(comment.gold),
          "model_failure",
          e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static final class TrustReport {
    boolean ok;
    int total;
    int uniqueIds;
    int duplicateIds;
    int scoredForF1;
    int policyCount;
    Map<String, Integer> splitCounts = new LinkedHashMap<>();
    Map<String, Integer> languageCounts = new LinkedHashMap<>();
    Map<String, Integer> redactCounts = new LinkedHashMap<>();
    List<String> failures = new ArrayList<>();
  }

  private static final class Metrics {
    int comments;
    int goldSpans;
    int predictedSpans;
    int truePositives;
    int falsePositives;
    int falseNegatives;
    int hardNegativeComments;
    int hardNegativeCorrect;

    static Metrics of(List<CommentResult> results, String languageOrNull, boolean excludePolicy) {
      Metrics m = new Metrics();
      for (CommentResult r : results) {
        if (languageOrNull != null && !languageOrNull.equals(r.comment.language)) {
          continue;
        }
        boolean isPolicy = "policy_decision".equals(r.comment.shouldRedact);
        if (excludePolicy && isPolicy) {
          continue;
        }
        if (!excludePolicy && !isPolicy) {
          // policy-only view
          continue;
        }
        m.comments++;
        m.goldSpans += r.comment.gold.size();
        m.predictedSpans += r.predicted.size();
        m.truePositives += r.truePositives.size();
        m.falsePositives += r.falsePositives.size();
        m.falseNegatives += r.falseNegatives.size();
        if ("no".equals(r.comment.shouldRedact)) {
          m.hardNegativeComments++;
          if (r.predicted.isEmpty()) {
            m.hardNegativeCorrect++;
          }
        }
      }
      return m;
    }

    double precision() {
      int denom = truePositives + falsePositives;
      return denom == 0 ? 0.0 : (double) truePositives / denom;
    }

    double recall() {
      int denom = truePositives + falseNegatives;
      return denom == 0 ? 0.0 : (double) truePositives / denom;
    }

    double f1() {
      double p = precision();
      double r = recall();
      return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    double hardNegativeAccuracy() {
      return hardNegativeComments == 0 ? 0.0 : (double) hardNegativeCorrect / hardNegativeComments;
    }

    boolean reconciles() {
      return truePositives + falseNegatives == goldSpans
          && truePositives + falsePositives == predictedSpans;
    }
  }
}

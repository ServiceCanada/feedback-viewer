package ca.gc.tbs.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ErrorKeywordService {
  private static final Logger LOG = LoggerFactory.getLogger(ErrorKeywordService.class);

  // Pre-compiled patterns for each language
  private Pattern englishPattern;
  private Pattern frenchPattern;
  private Pattern bilingualPattern;

  // Keep original keywords for logging and debugging
  private Set<String> englishKeywords = new HashSet<>();
  private Set<String> frenchKeywords = new HashSet<>();
  private Set<String> bilingualKeywords = new HashSet<>();

  public Set<String> getEnglishKeywords() {
    return englishKeywords;
  }

  public Set<String> getFrenchKeywords() {
    return frenchKeywords;
  }

  public Set<String> getBilingualKeywords() {
    return bilingualKeywords;
  }

  @PostConstruct
  public void init() {
    try {
      LOG.info("Starting to load error keywords...");

      // Load keywords
      englishKeywords = loadKeywords("static/error_keywords/errors_en.txt");
      frenchKeywords = loadKeywords("static/error_keywords/errors_fr.txt");
      bilingualKeywords = loadKeywords("static/error_keywords/errors_bilingual.txt");

      // Pre-compile patterns
      englishPattern = compilePattern(englishKeywords);
      frenchPattern = compilePattern(frenchKeywords);
      bilingualPattern = compilePattern(bilingualKeywords);

      LOG.info(
          "Successfully loaded and compiled patterns. Total keywords - English: {}, French: {},"
              + " Bilingual: {}",
          englishKeywords.size(),
          frenchKeywords.size(),
          bilingualKeywords.size());

      // Log samples for verification
      logSampleKeywords("English", englishKeywords);
      logSampleKeywords("French", frenchKeywords);
      logSampleKeywords("Bilingual", bilingualKeywords);

    } catch (Exception e) {
      LOG.error("Failed to initialize error keywords", e);
      throw e;
    }
  }

  private void logSampleKeywords(String type, Set<String> keywords) {
    LOG.info(
        "Loaded {} keywords. Size: {}, Sample: {}",
        type,
        keywords.size(),
        new ArrayList<>(keywords).subList(0, Math.min(5, keywords.size())));
  }

  private Pattern compilePattern(Set<String> keywords) {
    if (keywords.isEmpty()) {
      return Pattern.compile("$^"); // Pattern that matches nothing
    }

    // Join all keywords with OR operator and compile once
    String pattern =
        keywords.stream()
            .map(Pattern::quote)
            .reduce((a, b) -> a + "|" + b)
            .map(p -> "(?i)(" + p + ")")
            .orElse("$^");

    return Pattern.compile(pattern);
  }

  private Set<String> loadKeywords(String path) {
    Set<String> keywords = new HashSet<>();
    try {
      ClassPathResource resource = new ClassPathResource(path);
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.trim().isEmpty()) {
            keywords.add(line.trim().toLowerCase());
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load error keywords from " + path, e);
    }
    return keywords;
  }

  public boolean containsErrorKeywords(String text, String language) {
    if (text == null || text.isEmpty()) {
      LOG.debug("Text is null or empty");
      return false;
    }

    text = text.toLowerCase();
    LOG.debug("Checking text for error keywords: {}", text);
    LOG.debug("Language: {}", language);

    // Check bilingual keywords first using pre-compiled pattern
    if (bilingualPattern.matcher(text).find()) {
      LOG.debug("Found bilingual keyword match");
      return true;
    }

    // Check language-specific keywords using pre-compiled pattern
    Pattern languagePattern = "fr".equalsIgnoreCase(language) ? frenchPattern : englishPattern;
    if (languagePattern.matcher(text).find()) {
      LOG.debug("Found {} keyword match", language);
      return true;
    }

    LOG.debug("No error keywords found in text");
    return false;
  }
}

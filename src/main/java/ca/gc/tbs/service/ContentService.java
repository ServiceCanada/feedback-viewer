package ca.gc.tbs.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

@Service
public class ContentService {
  private static final Logger logger = LoggerFactory.getLogger(ContentService.class);

  // Pre-compiled regex patterns for better performance
  private static final Pattern POSTAL_CODE_PATTERN =
      Pattern.compile("[A-Za-z]\\s*\\d\\s*[A-Za-z]\\s*[ -]?\\s*\\d\\s*[A-Za-z]\\s*\\d");
  private static final Pattern PASSPORT_PATTERN = Pattern.compile("\\b([A-Za-z]{2}\\s*\\d{6})\\b");
  private static final Pattern SIN_PATTERN =
      Pattern.compile("(\\d{3}\\s*\\d{3}\\s*\\d{3}|\\d{3}\\D*\\d{3}\\D*\\d{3})");
  private static final Pattern PHONE_PATTERN_1 =
      Pattern.compile("(\\+\\d{1,2}\\s?)?1?\\-?\\.?\\s?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");
  private static final Pattern PHONE_PATTERN_2 =
      Pattern.compile(
          "(?:(?:\\+?1\\s*(?:[.-]\\s*)?)?(?:\\(\\s*([2-9]1[02-9]|[2-9][02-8]1|[2-9][02-8][02-9])\\s*\\)|([2-9]1[02-9]|[2-9][02-8]1|[2-9][02-8][02-9]))\\s*(?:[.-]\\s*)?)?([2-9]1[02-9]|[2-9][02-9]1|[2-9][02-9]{2})\\s*(?:[.-]\\s*)?([0-9]{4})(?:\\s*(?:#|x\\.?|ext\\.?|extension)\\s*(\\d+))?");
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("([a-zA-Z0-9_+\\-\\.]+)\\s*@\\s*([a-zA-Z0-9_\\-\\.]+)(?:\\s*[\\.,]\\s*([a-zA-Z]{0,10}))?");

  // Address patterns for English and French street addresses
  private static final Pattern ADDRESS_PATTERN_1 =
      Pattern.compile("(?i)\\b(\\d{1,6}[A-Za-z]?)\\s+([A-Za-z][A-Za-z''\\-]*(?:\\s+[A-Za-z][A-Za-z''\\-]*){0,3})\\s+" +
          "(?:st|street|ave|avenue|av|av\\.|rd|road|dr|drive|blvd|boulevard|boul|boul\\.|ln|lane|ct|court|pl|place|ter|terrace|terr|pkwy|parkway|cir|circle|hwy|highway|rue|chemin|ch|ch\\.|chem|chem\\.|route|rte|all[ée]e?|all\\.|allee|cours|voie|terrain|terrasse|rang|promenade|prom|prom\\.)" +
          "(?:\\s+(?:n|s|e|w|ne|nw|se|sw|o|no|so))?\\b");

  private static final Pattern ADDRESS_PATTERN_2 =
      Pattern.compile("(?i)\\b(\\d{1,6}[A-Za-z]?)\\s+(?:n|s|e|w|ne|nw|se|sw|o|no|so)\\s+([A-Za-z][A-Za-z''\\-]*(?:\\s+[A-Za-z][A-Za-z''\\-]*){0,3})\\s+" +
          "(?:st|street|ave|avenue|av|av\\.|rd|road|dr|drive|blvd|boulevard|boul|boul\\.|ln|lane|ct|court|pl|place|ter|terrace|terr|pkwy|parkway|cir|circle|hwy|highway|rue|chemin|ch|ch\\.|chem|chem\\.|route|rte|all[ée]e?|all\\.|allee|cours|voie|terrain|terrasse|rang|promenade|prom|prom\\.)\\b");

  private static final Pattern ADDRESS_PATTERN_3 =
      Pattern.compile("(?i)\\b(\\d{1,6}[A-Za-z]?)\\s+([A-Za-z][A-Za-z''\\-]*(?:\\s+[A-Za-z][A-Za-z''\\-]*){0,3})\\s+" +
          "(?:st|street|ave|avenue|av|av\\.|rd|road|dr|drive|blvd|boulevard|boul|boul\\.|ln|lane|ct|court|pl|place|ter|terrace|terr|pkwy|parkway|cir|circle|hwy|highway|rue|chemin|ch|ch\\.|chem|chem\\.|route|rte|all[ée]e?|all\\.|allee|cours|voie|terrain|terrasse|rang|promenade|prom|prom\\.)\\b");

  // Apache OpenNLP models
  private TokenizerModel tokenizerModel;
  private TokenNameFinderModel nerModel;

  private final BadWords badWords;

  @Autowired
  public ContentService(BadWords badWords) {
    this.badWords = badWords;
  }

  @PostConstruct
  public void init() {
    try {
      try (InputStream tokenStream = getClass().getResourceAsStream("/opennlp/en-token.bin")) {
        if (tokenStream != null) {
          tokenizerModel = new TokenizerModel(tokenStream);
          logger.info("OpenNLP tokenizer model loaded successfully");
        } else {
          logger.warn("OpenNLP tokenizer model not found at /opennlp/en-token.bin");
        }
      }
      try (InputStream nerStream = getClass().getResourceAsStream("/opennlp/en-ner-person.bin")) {
        if (nerStream != null) {
          nerModel = new TokenNameFinderModel(nerStream);
          logger.info("OpenNLP NER person model loaded successfully");
        } else {
          logger.warn("OpenNLP NER person model not found at /opennlp/en-ner-person.bin");
        }
      }
    } catch (IOException e) {
      logger.error("Error loading OpenNLP models", e);
    }
  }

  private Set<String> getAllowedWords() {
    return badWords.getAllowedWords();
  }

  public String cleanContent(String content) {
    if (content.isEmpty()) {
      return content;
    }
    content = StringUtils.normalizeSpace(content);

    var newContent = badWords.censor(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Profanity filtered");
    }

    newContent = this.cleanPostalCode(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Postal code cleaned");
    }

    newContent = this.cleanPhoneNumber(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Phone number cleaned");
    }

    newContent = this.cleanPassportNumber(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Passport number cleaned");
    }

    newContent = this.cleanSIN(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("SIN cleaned");
    }

    newContent = this.cleanEmailAddress(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Email address cleaned");
    }

    newContent = this.cleanStreetAddress(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Street address cleaned");
    }

    newContent = this.cleanNames(content);
    if (!newContent.contentEquals(content)) {
      content = newContent;
      logger.debug("Names cleaned");
    }

    return content;
  }

  private String cleanPostalCode(String content) {
    return POSTAL_CODE_PATTERN.matcher(content).replaceAll("### ###");
  }

  private String cleanPassportNumber(String content) {
    return PASSPORT_PATTERN.matcher(content).replaceAll("## ######");
  }

  private String cleanSIN(String content) {
    return SIN_PATTERN.matcher(content).replaceAll("### ### ###");
  }

  private String cleanPhoneNumber(String content) {
    content = PHONE_PATTERN_1.matcher(content).replaceAll("# ### ### ###");
    content = PHONE_PATTERN_2.matcher(content).replaceAll("# ### ### ###");
    return content;
  }

  private String cleanEmailAddress(String content) {
    return EMAIL_PATTERN.matcher(content).replaceAll("####@####.####");
  }

  private String cleanStreetAddress(String content) {
    content = ADDRESS_PATTERN_2.matcher(content).replaceAll("### #### ######");
    content = ADDRESS_PATTERN_1.matcher(content).replaceAll("### #### ######");
    content = ADDRESS_PATTERN_3.matcher(content).replaceAll("### #### ######");
    return content;
  }

  /**
   * Cleans person names from content using Apache OpenNLP entity recognition.
   */
  public String cleanNames(String content) {
    if (tokenizerModel == null || nerModel == null) {
      logger.warn("OpenNLP models not loaded, skipping name cleaning");
      return content;
    }

    try {
      // Tokenize the content
      var tokenizer = new TokenizerME(tokenizerModel);
      var tokens = tokenizer.tokenize(content);

      // Find person names
      var nameFinder = new NameFinderME(nerModel);
      var nameSpans = nameFinder.find(tokens);

      if (nameSpans.length == 0) {
        return content;
      }

      var commonPronouns =
          new HashSet<String>(Arrays.asList("he", "she", "him", "her", "his", "hers"));

      // Process spans in reverse order to preserve character offsets
      var spanList = new ArrayList<>(Arrays.asList(nameSpans));
      Collections.reverse(spanList);

      var sb = new StringBuilder(content);

      for (Span span : spanList) {
        // Build the name text from tokens
        var nameBuilder = new StringBuilder();
        for (int i = span.getStart(); i < span.getEnd(); i++) {
          if (i > span.getStart()) {
            nameBuilder.append(" ");
          }
          nameBuilder.append(tokens[i]);
        }
        String nameText = nameBuilder.toString();
        String nameLower = nameText.toLowerCase();

        // Check if allowed
        boolean isAllowed = getAllowedWords().contains(nameLower);
        if (!isAllowed && nameLower.contains(" ")) {
          for (String word : nameLower.split("\\s+")) {
            if (getAllowedWords().contains(word)) {
              isAllowed = true;
              break;
            }
          }
        }

        if (!commonPronouns.contains(nameLower) && !isAllowed) {
          // Find the name in the original content and replace
          int nameIndex = content.indexOf(nameText);
          if (nameIndex >= 0) {
            char[] replacement = new char[nameText.length()];
            Arrays.fill(replacement, '#');
            sb.replace(nameIndex, nameIndex + nameText.length(), new String(replacement));
          }
        }
      }

      nameFinder.clearAdaptiveData();
      return sb.toString();
    } catch (Exception e) {
      logger.error("Error during NLP processing", e);
      return content;
    }
  }
}

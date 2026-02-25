package ca.gc.tbs.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.gc.tbs.domain.BadWordEntry;
import ca.gc.tbs.repository.BadWordEntryRepository;

/**
 * Service for managing bad words, profanity filtering, threat detection, and allowed words.
 * Loads word lists from MongoDB into in-memory caches for fast lookup.
 * Thread-safe using ConcurrentHashMap with lazy initialization fallback.
 */
@Service
public class BadWords {
  private static final Logger logger = LoggerFactory.getLogger(BadWords.class);

  private final BadWordEntryRepository badWordEntryRepository;

  // In-memory caches for fast word lookup
  private final Set<String> profanityWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<String> threatWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<String> allowedWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<String> errorKeywords = Collections.newSetFromMap(new ConcurrentHashMap<>());

  // Combined set of all words to filter (profanity + threats)
  private final Set<String> allFilterWords = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private Pattern filterPattern;

  // Flag to track if data has been loaded (volatile for thread visibility)
  private volatile boolean isLoaded = false;
  private long lastLoadAttempt = 0;
  private static final long RETRY_INTERVAL_MS = 60000; // 1 minute
  private final Object loadLock = new Object();

  @Autowired
  public BadWords(BadWordEntryRepository badWordEntryRepository) {
    this.badWordEntryRepository = badWordEntryRepository;
  }

  /**
   * Ensures keywords are loaded from database.
   * Uses lazy initialization - loads on first access if @PostConstruct didn't run.
   * Thread-safe using double-checked locking.
   */
  private void ensureLoaded() {
    if (!isLoaded) {
      synchronized (loadLock) {
        if (!isLoaded) {
          long now = System.currentTimeMillis();
          if (now - lastLoadAttempt >= RETRY_INTERVAL_MS) {
            logger.info("Lazy initializing BadWords - loading from database");
            loadConfigs();
          }
        }
      }
    }
  }

  /**
   * Loads word configurations from MongoDB on service initialization.
   * Called automatically by Spring after dependency injection.
   */
  @PostConstruct
  public void loadConfigs() {
    logger.info("Loading word configurations from MongoDB...");
    lastLoadAttempt = System.currentTimeMillis();

    try {
      if (badWordEntryRepository == null) {
        throw new RuntimeException("BadWordEntryRepository not injected");
      }

      // Load profanity words
      List<BadWordEntry> profanityEntries = badWordEntryRepository.findByTypeAndActive("profanity", true);
      profanityEntries.forEach(entry -> {
        String word = entry.getWord().trim().toLowerCase();
        profanityWords.add(word);
        allFilterWords.add(word);
      });

      // Load threat words
      List<BadWordEntry> threatEntries = badWordEntryRepository.findByTypeAndActive("threat", true);
      threatEntries.forEach(entry -> {
        String word = entry.getWord().trim().toLowerCase();
        threatWords.add(word);
        allFilterWords.add(word);
      });

      // Load allowed words
      List<BadWordEntry> allowedEntries = badWordEntryRepository.findByTypeAndActive("allowed", true);
      allowedEntries.forEach(entry -> {
        String word = entry.getWord().trim().toLowerCase();
        allowedWords.add(word);
      });

      // Load error keywords
      List<BadWordEntry> errorEntries = badWordEntryRepository.findByTypeAndActive("error", true);
      errorEntries.forEach(entry -> {
        String word = entry.getWord().trim().toLowerCase();
        errorKeywords.add(word);
      });

      // Compile the filter pattern after all words are loaded
      compileFilterPattern();

      logger.info("Loaded {} profanity, {} threat, {} allowed, {} error keywords",
          profanityWords.size(), threatWords.size(), allowedWords.size(), errorKeywords.size());

      isLoaded = true;

    } catch (Exception e) {
      logger.error("Failed to load word configurations from MongoDB", e);
      // isLoaded remains false to allow retry on next access after backoff
    }
  }

  /**
   * Returns the set of allowed words that should not be redacted.
   *
   * @return Unmodifiable set of allowed words
   */
  public Set<String> getAllowedWords() {
    ensureLoaded();
    return Collections.unmodifiableSet(allowedWords);
  }

  /**
   * Returns the set of error keywords.
   *
   * @return Unmodifiable set of error keywords
   */
  public Set<String> getErrorKeywords() {
    ensureLoaded();
    return Collections.unmodifiableSet(errorKeywords);
  }

  /**
   * Returns the set of profanity words.
   *
   * @return Unmodifiable set of profanity words
   */
  public Set<String> getProfanityWords() {
    ensureLoaded();
    return Collections.unmodifiableSet(profanityWords);
  }

  /**
   * Returns the set of threat words.
   *
   * @return Unmodifiable set of threat words
   */
  public Set<String> getThreatWords() {
    ensureLoaded();
    return Collections.unmodifiableSet(threatWords);
  }

  private void compileFilterPattern() {
    if (allFilterWords.isEmpty()) {
      filterPattern = null;
      return;
    }
    String patternString = allFilterWords.stream()
            .filter(word -> word != null && !word.trim().isEmpty())
            .map(Pattern::quote)
            .map(word -> "\\b" + word + "\\b")
            .collect(Collectors.joining("|"));
    filterPattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  /**
   * Censors profanity and threats in the given text by replacing them with asterisks.
   * Words in the allowed words list are never censored.
   *
   * @param text The text to censor
   * @return The censored text
   */
  public String censor(String text) {
    ensureLoaded();
    if (text == null || text.isEmpty()) {
      return text;
    }
    if (filterPattern == null) {
      return text;
    }

    Matcher matcher = filterPattern.matcher(text);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String match = matcher.group();
      String normalized = match.toLowerCase().replaceAll("[^a-zà-ÿ]", "");

      if (allowedWords.contains(normalized)) {
        matcher.appendReplacement(result, Matcher.quoteReplacement(match));
      } else {
        matcher.appendReplacement(result, Matcher.quoteReplacement(createMask(match)));
      }
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Creates a mask of asterisks for a given word.
   */
  private String createMask(String word) {
    return word.replaceAll(".", "*");
  }

  /**
   * Reloads word configurations from MongoDB.
   * Useful for refreshing the cache without restarting the application.
   */
  public void reload() {
    logger.info("Reloading word configurations from MongoDB...");

    synchronized (loadLock) {
      profanityWords.clear();
      threatWords.clear();
      allowedWords.clear();
      errorKeywords.clear();
      allFilterWords.clear();
      filterPattern = null;
      isLoaded = false;
      loadConfigs();
    }
  }
}

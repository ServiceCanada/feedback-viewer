package ca.gc.tbs.controller;

import ca.gc.tbs.domain.TopTaskSurvey;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * The values stored in a top task survey field, so filters can match them exactly.
 *
 * <p>Cosmos DB can't use an index for a case-insensitive regex, so a filter written as one reads
 * every row in the date range (about 20,000 RU for one department over a quarter). Fields like
 * department and theme hold only a few dozen distinct spellings, so the filters pick the matching
 * spellings here and query them with an indexed {@code $in}, which returns the same rows. The
 * lists are refreshed in the background every 10 minutes.
 */
final class SurveyFieldValues {

  private static final Logger LOG = LoggerFactory.getLogger(SurveyFieldValues.class);

  private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(10);

  private final MongoTemplate mongoTemplate;
  private final LoadingCache<String, List<String>> values;

  SurveyFieldValues(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.values = Caffeine.newBuilder().refreshAfterWrite(REFRESH_INTERVAL).build(this::load);
  }

  /**
   * The stored values that a case-insensitive {@code ^name$} regex matches for one of the names.
   * Empty when none match or the values can't be loaded; callers then fall back to the regex.
   */
  List<String> equalToAnyIgnoringCase(String field, Collection<String> names) {
    List<Pattern> patterns =
        names.stream().map(name -> caseInsensitive("^" + Pattern.quote(name) + "$")).toList();
    return matching(field, value -> patterns.stream().anyMatch(p -> p.matcher(value).find()));
  }

  /**
   * The stored values that contain the text, ignoring case, as a case-insensitive regex of the
   * quoted text matches. Empty when none match or the values can't be loaded.
   */
  List<String> containingIgnoringCase(String field, String text) {
    Pattern pattern = caseInsensitive(Pattern.quote(text));
    return matching(field, value -> pattern.matcher(value).find());
  }

  private List<String> matching(String field, Predicate<String> matches) {
    List<String> stored;
    try {
      stored = values.get(field);
    } catch (RuntimeException e) {
      LOG.warn("Couldn't load the stored values of {}; filtering with a regex instead", field, e);
      return List.of();
    }
    return stored.stream().filter(matches).toList();
  }

  private List<String> load(String field) {
    Aggregation distinctValues =
        Aggregation.newAggregation(
            Aggregation.match(Criteria.where("processed").is("true")), Aggregation.group(field));
    List<String> stored = new ArrayList<>();
    for (Document value : mongoTemplate.aggregate(distinctValues, TopTaskSurvey.class, Document.class)) {
      if (value.get("_id") instanceof String text) {
        stored.add(text);
      }
    }
    return stored;
  }

  private static Pattern caseInsensitive(String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }
}

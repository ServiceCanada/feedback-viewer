package ca.gc.tbs.repository;

import ca.gc.tbs.domain.BadWordEntry;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing BadWordEntry entities in MongoDB. Provides methods to query words by type
 * and active status.
 */
@Repository
public interface BadWordEntryRepository extends MongoRepository<BadWordEntry, String> {

  /**
   * Find all active words of a specific type.
   *
   * @param type The type of words to find (profanity, threat, allowed, error)
   * @param active Whether the words should be active
   * @return List of matching BadWordEntry entities
   */
  List<BadWordEntry> findByTypeAndActive(String type, Boolean active);

  /**
   * Find all words of a specific type regardless of active status.
   *
   * @param type The type of words to find
   * @return List of matching BadWordEntry entities
   */
  List<BadWordEntry> findByType(String type);

  /**
   * Find all active words.
   *
   * @param active Whether the words should be active
   * @return List of matching BadWordEntry entities
   */
  List<BadWordEntry> findByActive(Boolean active);

  /**
   * Find all active words of a specific type and language.
   *
   * @param type The type of words to find
   * @param language The language ("en", "fr", or "both")
   * @param active Whether the words should be active
   * @return List of matching BadWordEntry entities
   */
  List<BadWordEntry> findByTypeAndLanguageAndActive(String type, String language, Boolean active);

  /**
   * Find a word entry by word text, language, and type. Used for duplicate detection before
   * creating or updating entries.
   *
   * @param word The word text (case-sensitive)
   * @param language The language ("en", "fr", or "both")
   * @param type The type of word (profanity, threat, allowed, error)
   * @return The matching BadWordEntry if found, null otherwise
   */
  BadWordEntry findByWordAndLanguageAndType(String word, String language, String type);
}

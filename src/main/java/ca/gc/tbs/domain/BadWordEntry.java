package ca.gc.tbs.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity representing a word entry in the badwords collection. Supports profanity filtering, threat
 * detection, allowed words, and error keywords.
 */
@Document(collection = "badwords")
@CompoundIndex(def = "{'type': 1, 'active': 1}")
@CompoundIndex(def = "{'type': 1, 'active': 1, 'language': 1}")
public class BadWordEntry {

  @Id private String id;

  @Indexed private String word;

  @Indexed private String language; // "en", "fr", or "both"

  @Indexed private String type; // "profanity", "threat", "allowed", "error"

  @Indexed private Boolean active; // true/false to enable/disable words

  public BadWordEntry() {}

  public BadWordEntry(String word, String language, String type, Boolean active) {
    this.word = word;
    this.language = language;
    this.type = type;
    this.active = active;
  }

  // Getters and Setters

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  @Override
  public String toString() {
    return "BadWordEntry{"
        + "id='"
        + id
        + '\''
        + ", word='"
        + word
        + '\''
        + ", language='"
        + language
        + '\''
        + ", type='"
        + type
        + '\''
        + ", active="
        + active
        + '}';
  }
}

package ca.gc.tbs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.BadWordEntry;
import ca.gc.tbs.repository.BadWordEntryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BadWordsTest {

  private BadWords badWords;

  @BeforeEach
  void setUp() {
    BadWordEntryRepository repository = mock(BadWordEntryRepository.class);
    when(repository.findByTypeAndActive(anyString(), eq(true))).thenReturn(List.of());
    when(repository.findByTypeAndActive("profanity", true))
        .thenReturn(List.of(new BadWordEntry("darn", "en", "profanity", true)));
    when(repository.findByTypeAndActive("threat", true))
        .thenReturn(List.of(new BadWordEntry("shot", "en", "threat", true)));
    badWords = new BadWords(repository);
    badWords.loadConfigs();
  }

  @Test
  void masksProfanity() {
    assertThat(badWords.censor("This darn page")).isEqualTo("This **** page");
  }

  @Test
  void leavesThreatWordsVisible() {
    // The daily threat report searches the cleaned text, so threat words must not be masked.
    assertThat(badWords.censor("Where do I book my flu shot"))
        .isEqualTo("Where do I book my flu shot");
    assertThat(badWords.getThreatWords()).containsExactly("shot");
  }
}

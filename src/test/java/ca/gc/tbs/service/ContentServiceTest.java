package ca.gc.tbs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentServiceTest {

  private ContentService contentService;

  @BeforeEach
  void setUp() {
    BadWords badWords = mock(BadWords.class);
    when(badWords.getAllowedWords()).thenReturn(Set.of());
    contentService = new ContentService(badWords);
    contentService.init();
  }

  @Test
  void redactsEveryOccurrenceOfARepeatedName() {
    String cleaned =
        contentService.cleanNames("John Smith called me. Later John Smith called again.");

    assertThat(cleaned).doesNotContain("John").doesNotContain("Smith");
  }

  @Test
  void redactsDistinctNamesAndKeepsTheRestOfTheText() {
    String content = "I spoke with John Smith and then with Mary Johnson about my file.";

    String cleaned = contentService.cleanNames(content);

    assertThat(cleaned)
        .isEqualTo("I spoke with ########## and then with ############ about my file.")
        .hasSameSizeAs(content);
  }

  @Test
  void leavesContentWithoutNamesUnchanged() {
    String content = "The page did not load when I tried to apply.";

    assertThat(contentService.cleanNames(content)).isEqualTo(content);
  }
}

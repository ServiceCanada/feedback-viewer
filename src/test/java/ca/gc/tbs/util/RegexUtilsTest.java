package ca.gc.tbs.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegexUtilsTest {

  @Test
  void metacharactersAreMatchedLiterally() {
    String input = "https://a.ca/x?q=(1)|[2]{3}*+^$\\";
    Pattern pattern = Pattern.compile(RegexUtils.literal(input), Pattern.CASE_INSENSITIVE);

    assertThat(pattern.matcher("prefix " + input + " suffix").find()).isTrue();
    assertThat(pattern.matcher("https://aXca/x?q=1").find()).isFalse();
  }

  @Test
  void costlyOrInvalidPatternsBecomePlainText() {
    assertThat(Pattern.compile(RegexUtils.literal("(a+)+$")).matcher("aaaaaaaa").find()).isFalse();
    assertThat(Pattern.compile(RegexUtils.literal("(")).matcher("x(y").find()).isTrue();
  }

  @Test
  void plainTextAndNullAreUnchanged() {
    assertThat(RegexUtils.literal("canada.ca/en/services")).isEqualTo("canada\\.ca/en/services");
    assertThat(RegexUtils.literal("Taxes / Impôts")).isEqualTo("Taxes / Impôts");
    assertThat(RegexUtils.literal(null)).isNull();
  }
}

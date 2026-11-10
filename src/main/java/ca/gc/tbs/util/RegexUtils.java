package ca.gc.tbs.util;

public final class RegexUtils {

  private RegexUtils() {}

  public static String literal(String input) {
    if (input == null) {
      return null;
    }
    return input.replaceAll("([\\\\.^$|()\\[\\]{}*+?])", "\\\\$1");
  }
}

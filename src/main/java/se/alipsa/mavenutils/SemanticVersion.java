package se.alipsa.mavenutils;

import org.apache.maven.artifact.versioning.ComparableVersion;

public class SemanticVersion implements Comparable<SemanticVersion> {

  private final String versionString;

  public SemanticVersion(String version) {
   this.versionString = version;
  }

  /**
   * Compares this object with the specified object for order.
   *
   * @param another the object to be compared.
   * @return &lt; 0 if smaller, 0 if equal, &gt; 0 if greater.
   */
  @Override
  public int compareTo(SemanticVersion another) {
    return new ComparableVersion(versionString).compareTo(new ComparableVersion(another.versionString));
  }

  /**
   * Compare two version strings
   *
   * @param first the first version
   * @param second the second version
   * @return &lt; 0 if smaller, 0 if equal, &gt; 0 if greater.
   */
  public static int compare(String first, String second) {
    if (first.startsWith("v")) {
      first = first.substring(1);
    }
    if (second.startsWith("v")) {
      second = second.substring(1);
    }
    if (first.contains("-jdk")) {
      first = first.substring(0, first.indexOf("-jdk"));
    }
    if (second.contains("-jdk")) {
      second = second.substring(0, second.indexOf("-jdk"));
    }
    if (first.contains(".jdk")) {
      first = first.substring(0, first.indexOf(".jdk"));
    }
    if (second.contains(".jdk")) {
      second = second.substring(0, second.indexOf(".jdk"));
    }

    return new ComparableVersion(first).compareTo(new ComparableVersion(second));
  }
}

package se.alipsa.mavenutils;

import java.util.Objects;

/**
 * Holds the result of comparing a given version against the latest available version
 * of a Maven artifact.
 */
public final class CompareResult {

  private final String latestVersion;
  private final int compareResult;

  /**
   * Creates a CompareResult.
   *
   * @param latestVersion the latest version found in the Maven repository
   * @param compareResult the result of comparing the given version with the latest version;
   *                      negative if the given version is older, zero if equal,
   *                      positive if the given version is newer
   */
  public CompareResult(String latestVersion, int compareResult) {
    this.latestVersion = latestVersion;
    this.compareResult = compareResult;
  }

  /**
   * Returns the latest version found in the Maven repository.
   *
   * @return the latest version
   */
  public String latestVersion() {
    return latestVersion;
  }

  /**
   * Returns the result of comparing the given version with the latest version.
   *
   * @return negative if the given version is older, zero if equal, positive if newer
   */
  public int compareResult() {
    return compareResult;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CompareResult)) return false;
    CompareResult that = (CompareResult) o;
    return compareResult == that.compareResult && Objects.equals(latestVersion, that.latestVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(latestVersion, compareResult);
  }

  @Override
  public String toString() {
    return "CompareResult[latestVersion=" + latestVersion + ", compareResult=" + compareResult + "]";
  }
}

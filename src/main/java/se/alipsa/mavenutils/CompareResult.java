package se.alipsa.mavenutils;

/**
 * Holds the result of comparing a given version against the latest available version
 * of a Maven artifact.
 *
 * @param latestVersion the latest version found in the Maven repository
 * @param compareResult the result of comparing the given version with the latest version;
 *                      negative if the given version is older, zero if equal,
 *                      positive if the given version is newer
 */
public record CompareResult(String latestVersion, int compareResult) {
}

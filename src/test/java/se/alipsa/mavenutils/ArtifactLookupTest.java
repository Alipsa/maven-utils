package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ArtifactLookupTest {

  @Test
  public void testFetchLatestVersionFromCentral() {
    ArtifactLookup lookup = new ArtifactLookup();
    String version = lookup.fetchLatestVersion("org.slf4j", "slf4j-api");
    assertNotNull(version);
    assertFalse(version.isEmpty());
  }

  @Test
  public void testFetchLatestVersionNotFound() {
    ArtifactLookup lookup = new ArtifactLookup();
    assertThrows(NotFoundException.class,
        () -> lookup.fetchLatestVersion("com.nonexistent.fake", "does-not-exist-artifact"));
  }

  @Test
  public void testFetchLatestVersionWithDependencyString() {
    ArtifactLookup lookup = new ArtifactLookup();
    String version = lookup.fetchLatestVersion("org.slf4j:slf4j-api");
    assertNotNull(version);
    assertFalse(version.isEmpty());
  }

  @Test
  public void testFetchLatestVersionMalformedString() {
    ArtifactLookup lookup = new ArtifactLookup();
    assertThrows(IllegalArgumentException.class,
        () -> lookup.fetchLatestVersion("onlyonepart"));
  }

  @Test
  public void testCompareWithLatest() {
    ArtifactLookup lookup = new ArtifactLookup();
    CompareResult result = lookup.compareWithLatest("org.slf4j", "slf4j-api", "1.0.0");
    assertNotNull(result.latestVersion());
    assertTrue(result.compareResult() < 0, "1.0.0 should be older than latest");
  }

  @Test
  public void testCompareWithLatestUsingDependencyString() {
    ArtifactLookup lookup = new ArtifactLookup();
    CompareResult result = lookup.compareWithLatest("org.slf4j:slf4j-api:1.0.0");
    assertNotNull(result.latestVersion());
    assertTrue(result.compareResult() < 0, "1.0.0 should be older than latest");
  }

  @Test
  public void testNetworkExceptionOnBadRepo() {
    ArtifactLookup lookup = new ArtifactLookup("http://nonexistent.invalid.host.example");
    assertThrows(NetworkException.class,
        () -> lookup.fetchLatestVersion("org.slf4j", "slf4j-api"));
  }

  @Test
  public void testCustomRepository() {
    ArtifactLookup lookup = new ArtifactLookup("https://repo1.maven.org/maven2");
    String version = lookup.fetchLatestVersion("org.slf4j", "slf4j-api");
    assertNotNull(version);
    assertFalse(version.isEmpty());
  }
}

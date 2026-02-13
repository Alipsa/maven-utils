package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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

  @Test
  public void testFetchLatestVersionRejectsDoctypeMetadata() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    String xml = "<?xml version=\"1.0\"?>\n"
        + "<!DOCTYPE metadata [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
        + "<metadata><versioning><release>&xxe;</release></versioning></metadata>";
    server.createContext("/repo/org/slf4j/slf4j-api/maven-metadata.xml", exchange -> {
      byte[] payload = xml.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/xml");
      exchange.sendResponseHeaders(200, payload.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(payload);
      }
    });
    server.start();
    try {
      ArtifactLookup lookup = new ArtifactLookup("http://localhost:" + server.getAddress().getPort() + "/repo/");
      assertThrows(NetworkException.class, () -> lookup.fetchLatestVersion("org.slf4j", "slf4j-api"));
    } finally {
      server.stop(0);
    }
  }
}

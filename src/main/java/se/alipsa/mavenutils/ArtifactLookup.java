package se.alipsa.mavenutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Looks up the latest version of a Maven artifact from a remote repository
 * and optionally compares it against a known version.
 */
public class ArtifactLookup {

  private static final Logger LOG = LoggerFactory.getLogger(ArtifactLookup.class);

  private final String repositoryUrl;

  /**
   * Creates an ArtifactLookup that queries Maven Central.
   */
  public ArtifactLookup() {
    this("https://repo1.maven.org/maven2/");
  }

  /**
   * Creates an ArtifactLookup that queries the given repository URL.
   *
   * @param repositoryUrl the base URL of the Maven repository
   */
  public ArtifactLookup(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
  }

  /**
   * Fetches the maven-metadata.xml for the given artifact.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @return the XML content as a string
   * @throws NotFoundException  if the artifact metadata is not found (HTTP 404)
   * @throws NetworkException   if a network error occurs
   */
  private String fetchMetadataXml(String groupId, String artifactId) {
    String path = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
    String url = repositoryUrl + path;
    LOG.debug("Fetching metadata from {}", url);

    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 404) {
        throw new NotFoundException("Artifact metadata not found: " + groupId + ":" + artifactId);
      }
      if (response.statusCode() != 200) {
        throw new NetworkException("Unexpected HTTP status " + response.statusCode() + " from " + url);
      }
      return response.body();
    } catch (NotFoundException | NetworkException e) {
      throw e;
    } catch (IOException e) {
      throw new NetworkException("Failed to fetch metadata from " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NetworkException("Interrupted while fetching metadata from " + url, e);
    } catch (IllegalArgumentException e) {
      throw new NetworkException("Invalid repository URL: " + url, e);
    }
  }

  /**
   * Fetches the latest version of the given artifact from the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @param packaging  the packaging type (e.g. "jar", "pom"); accepted for API symmetry but
   *                   does not affect the metadata lookup
   * @param classifier the classifier (e.g. "sources", "javadoc"); accepted for API symmetry but
   *                   does not affect the metadata lookup
   * @return the latest version string
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public String fetchLatestVersion(String groupId, String artifactId, String packaging, String classifier) {
    String xml = fetchMetadataXml(groupId, artifactId);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(xml)));

      NodeList releaseNodes = doc.getElementsByTagName("release");
      if (releaseNodes.getLength() > 0) {
        String release = releaseNodes.item(0).getTextContent().trim();
        if (!release.isEmpty()) {
          LOG.debug("Found release version {} for {}:{}", release, groupId, artifactId);
          return release;
        }
      }

      NodeList latestNodes = doc.getElementsByTagName("latest");
      if (latestNodes.getLength() > 0) {
        String latest = latestNodes.item(0).getTextContent().trim();
        if (!latest.isEmpty()) {
          LOG.debug("Found latest version {} for {}:{}", latest, groupId, artifactId);
          return latest;
        }
      }

      throw new NotFoundException("No release or latest version found for " + groupId + ":" + artifactId);
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new NetworkException("Failed to parse metadata XML for " + groupId + ":" + artifactId, e);
    }
  }

  /**
   * Fetches the latest version of the given artifact from the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @param packaging  the packaging type (e.g. "jar", "pom")
   * @return the latest version string
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public String fetchLatestVersion(String groupId, String artifactId, String packaging) {
    return fetchLatestVersion(groupId, artifactId, packaging, null);
  }

  /**
   * Fetches the latest version of the given artifact from the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @return the latest version string
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public String fetchLatestVersion(String groupId, String artifactId) {
    return fetchLatestVersion(groupId, artifactId, "jar");
  }

  /**
   * Fetches the latest version using a dependency string in the format
   * {@code groupId:artifactId[:packaging[:classifier]]}.
   *
   * @param dependencyString the dependency coordinates (minimum 2 parts, maximum 4)
   * @return the latest version string
   * @throws IllegalArgumentException if the dependency string is malformed
   * @throws NotFoundException        if the artifact or version metadata is not found
   * @throws NetworkException         if a network error occurs
   */
  public String fetchLatestVersion(String dependencyString) {
    String[] parts = dependencyString.split(":");
    if (parts.length < 2 || parts.length > 4) {
      throw new IllegalArgumentException(
          "Dependency string must have 2 to 4 parts (groupId:artifactId[:packaging[:classifier]]), got: "
              + dependencyString);
    }
    String groupId = parts[0];
    String artifactId = parts[1];
    String packaging = parts.length >= 3 ? parts[2] : "jar";
    String classifier = parts.length == 4 ? parts[3] : null;
    return fetchLatestVersion(groupId, artifactId, packaging, classifier);
  }

  /**
   * Compares the given version against the latest version available in the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @param version    the version to compare
   * @param packaging  the packaging type (e.g. "jar", "pom")
   * @param classifier the classifier (e.g. "sources", "javadoc")
   * @return a {@link CompareResult} containing the latest version and comparison result
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public CompareResult compareWithLatest(String groupId, String artifactId, String version,
                                         String packaging, String classifier) {
    String latestVersion = fetchLatestVersion(groupId, artifactId, packaging, classifier);
    int result = SemanticVersion.compare(version, latestVersion);
    return new CompareResult(latestVersion, result);
  }

  /**
   * Compares the given version against the latest version available in the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @param version    the version to compare
   * @param packaging  the packaging type (e.g. "jar", "pom")
   * @return a {@link CompareResult} containing the latest version and comparison result
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public CompareResult compareWithLatest(String groupId, String artifactId, String version, String packaging) {
    return compareWithLatest(groupId, artifactId, version, packaging, null);
  }

  /**
   * Compares the given version against the latest version available in the repository.
   *
   * @param groupId    the group ID of the artifact
   * @param artifactId the artifact ID
   * @param version    the version to compare
   * @return a {@link CompareResult} containing the latest version and comparison result
   * @throws NotFoundException if the artifact or version metadata is not found
   * @throws NetworkException  if a network error occurs
   */
  public CompareResult compareWithLatest(String groupId, String artifactId, String version) {
    return compareWithLatest(groupId, artifactId, version, "jar");
  }

  /**
   * Compares the given version against the latest version using a dependency string in the format
   * {@code groupId:artifactId:version[:packaging[:classifier]]}.
   *
   * @param dependencyString the dependency coordinates (minimum 3 parts, maximum 5)
   * @return a {@link CompareResult} containing the latest version and comparison result
   * @throws IllegalArgumentException if the dependency string is malformed
   * @throws NotFoundException        if the artifact or version metadata is not found
   * @throws NetworkException         if a network error occurs
   */
  public CompareResult compareWithLatest(String dependencyString) {
    String[] parts = dependencyString.split(":");
    if (parts.length < 3 || parts.length > 5) {
      throw new IllegalArgumentException(
          "Dependency string must have 3 to 5 parts (groupId:artifactId:version[:packaging[:classifier]]), got: "
              + dependencyString);
    }
    String groupId = parts[0];
    String artifactId = parts[1];
    String version = parts[2];
    String packaging = parts.length >= 4 ? parts[3] : "jar";
    String classifier = parts.length == 5 ? parts[4] : null;
    return compareWithLatest(groupId, artifactId, version, packaging, classifier);
  }
}

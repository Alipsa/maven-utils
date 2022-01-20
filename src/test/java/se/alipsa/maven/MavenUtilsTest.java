package se.alipsa.maven;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenUtilsTest {

  @Test
  public void testDownloadArtifact() throws SettingsBuildingException, ArtifactResolutionException {
    MavenUtils mavenUtils = new MavenUtils();
    File file = mavenUtils.resolveArtifact("org.slf4j", "slf4j-api", null, "jar", "1.7.32");
    assertTrue(file.exists(), "File does not exist");
    assertEquals("slf4j-api-1.7.32.jar", file.getName(), "File name is wrong");
  }

  @Test
  public void parseSimplePom() throws URISyntaxException, SettingsBuildingException, ModelBuildingException {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Model model = mavenUtils.parsePom(pomFile);
    assertNotNull(model, "model is null");
    Dependency dependency = model.getDependencies().stream().filter(d -> "slf4j-api".equals(d.getArtifactId())).findAny().orElse(null);
    assertNotNull(dependency, "dependency for slf4j-api not found in the model");
    assertEquals("org.slf4j", dependency.getGroupId(), "groupId is wrong");
    assertEquals("1.7.32", dependency.getVersion(), "version is wrong");
    assertEquals("jar",  dependency.getType(), "Type is wrong");
    assertEquals("compile", dependency.getScope(), "Scope is wrong");
  }

  @Test
  public void resolveSimplePom() throws SettingsBuildingException, ModelBuildingException, DependenciesResolveException, URISyntaxException {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");
    assertEquals(7, dependencies.size(), "number of dependencies (including transients)");
    dependencies.forEach(file -> assertTrue(file.exists(), file.getAbsolutePath() + " does not exist"));

    List<String> fileNames = dependencies.stream().map(File::getName).collect(Collectors.toList());
    assertTrue(fileNames.contains("slf4j-api-1.7.32.jar"), "slf4j-api-1.7.32.jar is missing");
    assertTrue(fileNames.contains("apiguardian-api-1.1.2.jar"), "apiguardian-api-1.1.2.jar is missing");
    assertTrue(fileNames.contains("junit-jupiter-api-5.8.2.jar"), "junit-jupiter-api-5.8.2.jar is missing");
    assertTrue(fileNames.contains("junit-platform-commons-1.8.2.jar"), "junit-platform-commons-1.8.2.jar is missing");
    assertTrue(fileNames.contains("junit-jupiter-5.8.2.jar"), "junit-jupiter-5.8.2.jar is missing");
    assertTrue(fileNames.contains("opentest4j-1.2.0.jar"), "opentest4j-1.2.0.jar is missing");
    assertTrue(fileNames.contains("junit-jupiter-params-5.8.2.jar"), "junit-jupiter-params-5.8.2.jar is missing");
  }

  @Test
  public void runMaven() throws URISyntaxException, MavenInvocationException {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    InvocationResult result = MavenUtils.runMaven(pomFile, new String[]{"validate"}, null, null);
    assertEquals(0, result.getExitCode(), "exit code");
  }

  @Test
  public void getClassloader() throws Exception {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    ClassLoader loader = mavenUtils.getMavenDependenciesClassloader(pomFile, null);
    Class<?> loggerClass = loader.loadClass("org.slf4j.Logger");
    assertNotNull(loggerClass);
  }
}

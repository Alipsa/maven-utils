package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenUtilsTest {

  private static final Logger LOG = LoggerFactory.getLogger(MavenUtilsTest.class);

  @Test
  public void testDownloadArtifact() throws SettingsBuildingException, ArtifactResolutionException {
    MavenUtils mavenUtils = new MavenUtils();
    File localRepo = MavenUtils.getLocalRepository().getBasedir();
    assertNotNull(localRepo);
    File artifactDir = new File(localRepo, "org/slf4j/slf4j-api/1.7.32");
    if (artifactDir.exists()) {
      LOG.info("Deleting files in {} to ensure remote download works", artifactDir.getAbsolutePath());
      for (File file : Objects.requireNonNull(artifactDir.listFiles())) {
        if ("slf4j-api-1.7.32.jar".equals(file.getName()) || "slf4j-api-1.7.32.jar.sha1".equals(file.getName())) {
          LOG.info("Deleting {}", file.getName());
          assertTrue(file.delete(), "Failed to delete " + file.getAbsolutePath());
        }
      }
    } else {
      LOG.info("{} does not exist: no problem, we are going to fetch it!", artifactDir.getAbsolutePath());
    }
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
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile, true);
    assertNotNull(dependencies, "Failed to resolve dependencies");
    assertEquals(9, dependencies.size(), "number of dependencies (including transients)");
    dependencies.forEach(file -> assertTrue(file.exists(), file.getAbsolutePath() + " does not exist"));

    List<String> fileNames = dependencies.stream().map(File::getName).collect(Collectors.toList());
    //System.out.println(fileNames);
    assertTrue(fileNames.contains("slf4j-api-1.7.32.jar"), "slf4j-api-1.7.32.jar is missing");
    assertTrue(fileNames.contains("apiguardian-api-1.1.2.jar"), "apiguardian-api-1.1.2.jar is missing");
    assertTrue(fileNames.contains("junit-jupiter-api-5.8.2.jar"), "junit-jupiter-api-5.8.2.jar is missing");
    assertTrue(fileNames.contains("junit-platform-commons-1.8.2.jar"), "junit-platform-commons-1.8.2.jar is missing");
    assertTrue(fileNames.contains("junit-platform-engine-1.8.2.jar"), "junit-platform-engine-1.8.2.jar is missing");
    assertTrue(fileNames.contains("junit-jupiter-engine-5.8.2.jar"), "junit-jupiter-engine-5.8.2.jar is missing");
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

  @Test
  public void testPomParsing() throws Exception {
    File projectPomFile = new File(getClass().getResource("/pom/pom.xml").getFile());
    LOG.info("pom file is {}", projectPomFile);
    MavenUtils mavenUtils = new MavenUtils();
    Model project = mavenUtils.parsePom(projectPomFile);
    assertEquals("phone-number", project.getArtifactId());
    assertEquals("1.8", project.getProperties().getProperty("maven.compiler.source"));

    LocalRepository localRepository = MavenUtils.getLocalRepository();
    assertNotNull(localRepository);
    LOG.info("local Maven repository set to {}", localRepository.getBasedir());
  }

  @Test
  public void testBomDependency() throws IOException, SettingsBuildingException, ModelBuildingException, XmlPullParserException {
    File pomFile = new File(getClass().getResource("/pom/pom_bom.xml").getFile());
    MavenUtils mavenUtils = new MavenUtils();
    Model project = mavenUtils.parsePom(pomFile);
    Dependency dep = project.getDependencies().stream().filter(
            a -> "org.junit.jupiter".equals(a.getGroupId()) && "junit-jupiter".equals(a.getArtifactId()))
        .findAny()
        .orElse(null);
    assertNotNull(dep, "Failed to resolve junit dependency");
    assertEquals("org.junit.jupiter:junit-jupiter:5.8.2", dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion());
  }

  @Test
  public void testPomWithParent() throws IOException, SettingsBuildingException, ModelBuildingException, XmlPullParserException {
    File pomFile = new File(getClass().getResource("/pom/pom_parent.xml").getFile());
    MavenUtils mavenUtils = new MavenUtils();
    Model project = mavenUtils.parsePom(pomFile);
    Dependency dep = project.getDependencies().stream().filter(
            a -> "org.junit.jupiter".equals(a.getGroupId()) && "junit-jupiter-api".equals(a.getArtifactId()))
        .findAny()
        .orElse(null);
    assertNotNull(dep, "Failed to resolve junit dependency");
    assertEquals("org.junit.jupiter:junit-jupiter-api:5.8.2", dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion());

    dep = project.getDependencies().stream().filter(
            a -> "org.springframework.boot".equals(a.getGroupId()) && "spring-boot-starter-test".equals(a.getArtifactId()))
        .findAny()
        .orElse(null);
    assertNotNull(dep, "Failed to resolve spring boot dependency");
    assertEquals("org.springframework.boot:spring-boot-starter-test:2.6.1", dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion());
  }

  @Test
  public void testPomClassLoader() throws Exception {
    String className = "com.google.i18n.phonenumbers.Phonenumber";
    MavenUtils mavenUtils = new MavenUtils();
    try {
      this.getClass().getClassLoader().loadClass(className);
      fail("Loading the class should not have worked");
    } catch (Exception e) {
      assertTrue(e instanceof ClassNotFoundException);
    }
    File projectPomFile = new File(getClass().getResource("/pom/pom.xml").getFile());
    ClassLoader cl = mavenUtils.getMavenDependenciesClassloader(projectPomFile, getClass().getClassLoader());
    Class<?> clazz = cl.loadClass(className);
    LOG.info("Class resolved to {}", clazz);
  }

  /*@Test
  public void testPomParsingWithPomArtifact() throws URISyntaxException, SettingsBuildingException, ModelBuildingException, DependenciesResolveException {
    File pomFile = Paths.get(getClass().getResource("/pom/transientPom.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");
  }
   */
}

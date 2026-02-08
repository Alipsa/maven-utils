package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class MavenUtilsTest {

  private static final Logger LOG = LoggerFactory.getLogger(MavenUtilsTest.class);

  @Test
  public void testDownloadArtifact() throws Exception {
    try (SystemPropertyOverride ignored = overrideSystemProperty("user.home", Files.createTempDirectory("fake-user-home").toString())) {
      File localRepo = Files.createTempDirectory("isolated-local-repo").toFile();
      createUserSettingsWithLocalRepo(new File(System.getProperty("user.home")), localRepo);

      File remoteRepoRoot = Files.createTempDirectory("release-remote-repo").toFile();
      createArtifactInFileRepo(remoteRepoRoot, "org.slf4j", "slf4j-api", "1.7.32");

      MavenUtils mavenUtils = new MavenUtils(List.of());
      mavenUtils.addRemoteRepository("release-repo", remoteRepoRoot.toURI().toString());

      File file = mavenUtils.resolveArtifact("org.slf4j", "slf4j-api", null, "jar", "1.7.32");
      assertTrue(file.exists(), "File does not exist");
      assertEquals("slf4j-api-1.7.32.jar", file.getName(), "File name is wrong");
      assertTrue(file.getAbsolutePath().startsWith(localRepo.getAbsolutePath()),
          "Artifact should be resolved to isolated local repo but was " + file.getAbsolutePath());
    }
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
  public void runMavenWithJavaHomeAndConsumers() throws URISyntaxException, MavenInvocationException {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    File javaHome = new File(System.getProperty("java.home"));

    List<String> outLines = new java.util.ArrayList<>();
    List<String> errLines = new java.util.ArrayList<>();

    // Explicitly type the consumers to resolve method ambiguity
    java.util.function.Consumer<String> outConsumer = outLines::add;
    java.util.function.Consumer<String> errConsumer = errLines::add;

    int exitCode = MavenUtils.runMaven(
        pomFile,
        new String[]{"validate"},
        javaHome,
        outConsumer,
        errConsumer
    );

    assertEquals(0, exitCode, "exit code should be 0 for successful build");
    assertTrue(outLines.size() > 0 || errLines.size() > 0, "should have captured some output");
  }

  @Test
  public void buildInvocationRequestSeparatesGoalsAndFlags() throws URISyntaxException {
    File pomFile = Paths.get(getClass().getResource("/pom/simple.xml").toURI()).toFile();
    File javaHome = new File(System.getProperty("java.home"));
    InvocationRequest request = MavenUtils.buildInvocationRequest(
        pomFile,
        new String[]{"-DskipTests", "-Dfoo=bar=baz", "-nsu", "-Pabc,def", "-pl", "module-a,module-b", "-rf", "module-c", "-q", "validate"},
        javaHome
    );

    assertEquals(List.of("validate"), request.getGoals());
    assertEquals("", request.getProperties().getProperty("skipTests"));
    assertEquals("bar=baz", request.getProperties().getProperty("foo"));
    assertEquals(javaHome, request.getJavaHome());
    assertTrue(request.isQuiet());
    assertEquals(List.of("abc", "def"), request.getProfiles());
    assertEquals(List.of("module-a", "module-b"), request.getProjects());
    assertEquals("module-c", request.getResumeFrom());
    assertNotNull(request.getArgs());
    assertTrue(request.getArgs().contains("-nsu"));
  }

  @Test
  public void resolveMavenHomeFromExecutableUsesReportedHome() throws Exception {
    File tempDir = Files.createTempDirectory("mvn-home").toFile();
    File mvnScript = new File(tempDir, "mvn");
    String expectedHome = new File(tempDir, "apache-maven").getAbsolutePath();
    String script = "#!/usr/bin/env bash\n" +
        "echo \"" + expectedHome + "\"\n";
    Files.writeString(mvnScript.toPath(), script);
    assertTrue(mvnScript.setExecutable(true));

    String resolved = MavenUtils.resolveMavenHomeFromExecutable(mvnScript);
    assertEquals(expectedHome, resolved);
  }

  @Test
  public void wrapperBeatsConfiguredHome() throws IOException {
    File projectDir = Files.createTempDirectory("wrapper-wins").toFile();
    createMinimalPom(projectDir);
    createWrapper(projectDir, new File(projectDir, "fake-wrapper-home").getAbsolutePath(), 0);
    File configuredHome = Files.createTempDirectory("configured-home").toFile();
    MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(projectDir, configuredHome, true);

    MavenUtils.MavenDistributionSelection selection = MavenUtils.selectMavenDistribution(new File(projectDir, "pom.xml"), options);
    assertEquals(MavenUtils.MavenDistributionMode.WRAPPER, selection.getMode());
    assertNotNull(selection.getMavenExecutable());
  }

  @Test
  public void configuredHomeUsedWhenNoWrapper() throws IOException {
    File projectDir = Files.createTempDirectory("home-wins").toFile();
    createMinimalPom(projectDir);
    File configuredHome = Files.createTempDirectory("configured-home").toFile();
    MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(projectDir, configuredHome, true);

    MavenUtils.MavenDistributionSelection selection = MavenUtils.selectMavenDistribution(new File(projectDir, "pom.xml"), options);
    assertEquals(MavenUtils.MavenDistributionMode.HOME, selection.getMode());
    assertEquals(configuredHome.getAbsolutePath(), selection.getMavenHome().getAbsolutePath());
  }

  @Test
  public void defaultUsedWhenWrapperAndConfiguredHomeAreMissing() throws IOException {
    File projectDir = Files.createTempDirectory("default-wins").toFile();
    createMinimalPom(projectDir);
    MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(projectDir, null, true);

    MavenUtils.MavenDistributionSelection selection = MavenUtils.selectMavenDistribution(new File(projectDir, "pom.xml"), options);
    assertEquals(MavenUtils.MavenDistributionMode.DEFAULT, selection.getMode());
  }

  @Test
  public void runMavenAndResolveDependenciesUseSamePrecedence() throws Exception {
    File projectDir = Files.createTempDirectory("wrapper-precedence").toFile();
    File pomFile = createMinimalPom(projectDir);
    createWrapper(projectDir, new File(projectDir, "fake-wrapper-home").getAbsolutePath(), 0);
    File configuredHome = Files.createTempDirectory("configured-home").toFile();
    MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(projectDir, configuredHome, true);

    MavenUtils.MavenRunResult runResult = MavenUtils.runMavenWithSelection(
        pomFile,
        new String[]{"validate"},
        null,
        options,
        line -> { },
        line -> { }
    );
    assertEquals(0, runResult.getInvocationResult().getExitCode());
    assertEquals(MavenUtils.MavenDistributionMode.WRAPPER, runResult.getDistributionSelection().getMode());

    MavenUtils mavenUtils = new MavenUtils();
    MavenUtils.DependenciesResolutionResult resolveResult = mavenUtils.resolveDependenciesWithSelection(pomFile, options);
    assertEquals(MavenUtils.MavenDistributionMode.WRAPPER, resolveResult.getDistributionSelection().getMode());
    assertNotNull(resolveResult.getDependencies());
  }

  @Test
  public void resolveDependenciesWithSelectionUsesWrapperMavenHomeForSettings() throws Exception {
    String originalUserHome = System.getProperty("user.home");
    File fakeUserHome = Files.createTempDirectory("fake-user-home").toFile();
    System.setProperty("user.home", fakeUserHome.getAbsolutePath());
    try {
      File projectDir = Files.createTempDirectory("wrapper-settings").toFile();
      File pomFile = new File(projectDir, "pom.xml");

      String groupId = "com.example";
      String artifactId = "demo-artifact";
      String version = "1.0.0";

      File fileRepo = Files.createTempDirectory("file-repo").toFile();
      createArtifactInFileRepo(fileRepo, groupId, artifactId, version);
      createPomWithRepositoryAndDependency(pomFile, fileRepo, groupId, artifactId, version);

      File configuredLocalRepo = Files.createTempDirectory("configured-local-repo").toFile();
      File wrapperLocalRepo = Files.createTempDirectory("wrapper-local-repo").toFile();
      File configuredHome = createMavenHomeWithLocalRepo(configuredLocalRepo);
      File wrapperHome = createMavenHomeWithLocalRepo(wrapperLocalRepo);
      createWrapper(projectDir, wrapperHome.getAbsolutePath(), 0);

      MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(projectDir, configuredHome, true);

      MavenUtils mavenUtils = new MavenUtils();
      MavenUtils.DependenciesResolutionResult result = mavenUtils.resolveDependenciesWithSelection(pomFile, options);

      assertEquals(MavenUtils.MavenDistributionMode.WRAPPER, result.getDistributionSelection().getMode());
      assertEquals(1, result.getDependencies().size(), "Expected one resolved dependency");

      File resolvedArtifact = result.getDependencies().iterator().next();
      assertTrue(resolvedArtifact.exists(), "Resolved artifact does not exist");
      assertTrue(resolvedArtifact.getAbsolutePath().startsWith(wrapperLocalRepo.getAbsolutePath()),
          "Expected artifact in wrapper local repo but got " + resolvedArtifact.getAbsolutePath());
      assertFalse(resolvedArtifact.getAbsolutePath().startsWith(configuredLocalRepo.getAbsolutePath()),
          "Artifact should not be resolved in configured local repo");
    } finally {
      if (originalUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", originalUserHome);
      }
    }
  }

  @Test
  public void wrapperDetectionSupportsUnixAndWindowsScripts() throws IOException {
    File unixProjectDir = Files.createTempDirectory("wrapper-unix").toFile();
    Files.createDirectories(unixProjectDir.toPath().resolve(".mvn/wrapper"));
    Files.writeString(unixProjectDir.toPath().resolve(".mvn/wrapper/maven-wrapper.properties"), "distributionUrl=https://example.invalid");
    Path unixWrapper = unixProjectDir.toPath().resolve("mvnw");
    Files.writeString(unixWrapper, "#!/usr/bin/env bash\nexit 0\n");
    assertTrue(unixWrapper.toFile().setExecutable(true));

    File windowsProjectDir = Files.createTempDirectory("wrapper-win").toFile();
    Files.createDirectories(windowsProjectDir.toPath().resolve(".mvn/wrapper"));
    Files.writeString(windowsProjectDir.toPath().resolve(".mvn/wrapper/maven-wrapper.properties"), "distributionUrl=https://example.invalid");
    Path winWrapper = windowsProjectDir.toPath().resolve("mvnw.cmd");
    Files.writeString(winWrapper, "@echo off\r\nexit /b 0\r\n");

    assertNotNull(MavenUtils.findWrapperExecutable(unixProjectDir));
    assertNotNull(MavenUtils.findWrapperExecutable(windowsProjectDir));
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

  @Test
  public void testFetchArtifactFromSnapshot() throws Exception {
    try (SystemPropertyOverride ignored = overrideSystemProperty("user.home", Files.createTempDirectory("fake-user-home").toString())) {
      File localRepo = Files.createTempDirectory("isolated-local-repo").toFile();
      createUserSettingsWithLocalRepo(new File(System.getProperty("user.home")), localRepo);

      File remoteRepoRoot = Files.createTempDirectory("snapshot-remote-repo").toFile();
      String groupId = "se.alipsa";
      String artifactId = "gade-runner";
      String version = "1.0.0-SNAPSHOT";
      String timestamp = "20260208.194500";
      String buildNumber = "1";
      createSnapshotArtifactInRepository(remoteRepoRoot, groupId, artifactId, version, timestamp, buildNumber);

      MavenUtils mavenUtils = new MavenUtils(List.of());
      mavenUtils.addRemoteRepository("local-snapshots", remoteRepoRoot.toURI().toString());

      File af = mavenUtils.resolveArtifact(groupId, artifactId, version);
      assertNotNull(af, "Artifact is null");
      assertTrue(af.exists(), "Resolved snapshot artifact must exist");
      assertTrue(af.getName().startsWith("gade-runner-1.0.0-") && af.getName().endsWith(".jar"),
          "Resolved snapshot artifact should be timestamped, got " + af.getName());
      assertTrue(af.getAbsolutePath().startsWith(localRepo.getAbsolutePath()),
          "Artifact should be resolved to isolated local repo but was " + af.getAbsolutePath());

      Path resolverStatus = localRepo.toPath()
          .resolve("se/alipsa/gade-runner/1.0.0-SNAPSHOT/resolver-status.properties");
      assertTrue(Files.exists(resolverStatus),
          "Snapshot resolution should write resolver-status.properties in isolated local repo");
    }
  }

  /**
   * Opt-in system integration test that verifies real artifact resolution from Maven Central.
   * Enable with -Dmavenutils.runSystemIT=true
   */
  @Test
  @EnabledIfSystemProperty(named = "mavenutils.runSystemIT", matches = "true")
  public void systemIntegrationDownloadArtifactFromCentral() throws Exception {
    try (SystemPropertyOverride ignored = overrideSystemProperty("user.home", Files.createTempDirectory("system-it-user-home").toString())) {
      File localRepo = Files.createTempDirectory("system-it-local-repo").toFile();
      createUserSettingsWithLocalRepo(new File(System.getProperty("user.home")), localRepo);

      MavenUtils mavenUtils = new MavenUtils();
      File file = mavenUtils.resolveArtifact("org.slf4j", "slf4j-api", null, "jar", "1.7.32");
      assertNotNull(file, "Artifact file is null");
      assertTrue(file.exists(), "Artifact file does not exist");
      assertEquals("slf4j-api-1.7.32.jar", file.getName(), "Unexpected artifact filename");
      assertTrue(file.getAbsolutePath().startsWith(localRepo.getAbsolutePath()),
          "Artifact should resolve into isolated local repo but was " + file.getAbsolutePath());
    }
  }

  /**
   * Opt-in system integration test that verifies real release artifact resolution.
   * Enable with -Dmavenutils.runSystemIT=true
   */
  @Test
  @EnabledIfSystemProperty(named = "mavenutils.runSystemIT", matches = "true")
  public void systemIntegrationFetchReleaseArtifact() throws Exception {
    try (SystemPropertyOverride ignored = overrideSystemProperty("user.home", Files.createTempDirectory("system-it-user-home").toString())) {
      File localRepo = Files.createTempDirectory("system-it-local-repo").toFile();
      createUserSettingsWithLocalRepo(new File(System.getProperty("user.home")), localRepo);

      String releaseRepoUrl = System.getProperty(
          "mavenutils.systemIT.releaseRepoUrl",
          "https://repo1.maven.org/maven2/"
      );
      String groupId = System.getProperty("mavenutils.systemIT.releaseGroupId", "se.alipsa");
      String artifactId = System.getProperty("mavenutils.systemIT.releaseArtifactId", "maven-utils");
      String version = System.getProperty("mavenutils.systemIT.releaseVersion", "1.3.0");

      assertFalse(version.endsWith("-SNAPSHOT"),
          "System IT release version must not be a SNAPSHOT, got " + version);

      MavenUtils mavenUtils = new MavenUtils(List.of());
      mavenUtils.addRemoteRepository("system-it-release", releaseRepoUrl);

      try {
        File af = mavenUtils.resolveArtifact(groupId, artifactId, version);
        assertNotNull(af, "Artifact is null");
        assertTrue(af.exists(), "Resolved release artifact must exist");
        assertEquals(artifactId + "-" + version + ".jar", af.getName(),
            "Resolved release artifact filename mismatch");
        assertTrue(af.getAbsolutePath().startsWith(localRepo.getAbsolutePath()),
            "Artifact should resolve into isolated local repo but was " + af.getAbsolutePath());
      } catch (ArtifactResolutionException e) {
        fail("Failed to resolve release artifact "
                + groupId + ":" + artifactId + ":" + version
                + " from repository " + releaseRepoUrl
                + ". Override with -Dmavenutils.systemIT.releaseRepoUrl/groupId/artifactId/version. Cause: "
                + e.getMessage());
      }
    }
  }

  /*
  @Test
  public void testPomParsingWithPomArtifact() throws URISyntaxException, SettingsBuildingException, ModelBuildingException, DependenciesResolveException {
    File pomFile = Paths.get(getClass().getResource("/pom/transientPom.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");
  }

   */

  private static File createMinimalPom(File projectDir) throws IOException {
    File pomFile = new File(projectDir, "pom.xml");
    Files.writeString(
        pomFile.toPath(),
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>se.alipsa</groupId>\n"
            + "  <artifactId>temp-project</artifactId>\n"
            + "  <version>1.0.0</version>\n"
            + "</project>\n"
    );
    return pomFile;
  }

  private static void createPomWithRepositoryAndDependency(File pomFile, File repoDir, String groupId, String artifactId, String version)
      throws IOException {
    Files.writeString(
        pomFile.toPath(),
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>se.alipsa</groupId>\n"
            + "  <artifactId>settings-precedence</artifactId>\n"
            + "  <version>1.0.0</version>\n"
            + "  <repositories>\n"
            + "    <repository>\n"
            + "      <id>test-file-repo</id>\n"
            + "      <url>" + repoDir.toURI() + "</url>\n"
            + "    </repository>\n"
            + "  </repositories>\n"
            + "  <dependencies>\n"
            + "    <dependency>\n"
            + "      <groupId>" + groupId + "</groupId>\n"
            + "      <artifactId>" + artifactId + "</artifactId>\n"
            + "      <version>" + version + "</version>\n"
            + "    </dependency>\n"
            + "  </dependencies>\n"
            + "</project>\n"
    );
  }

  private static void createArtifactInFileRepo(File repoDir, String groupId, String artifactId, String version) throws IOException {
    Path artifactDir = repoDir.toPath()
        .resolve(groupId.replace('.', File.separatorChar))
        .resolve(artifactId)
        .resolve(version);
    Files.createDirectories(artifactDir);
    Path pomPath = artifactDir.resolve(artifactId + "-" + version + ".pom");
    Files.writeString(
        pomPath,
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>" + groupId + "</groupId>\n"
            + "  <artifactId>" + artifactId + "</artifactId>\n"
            + "  <version>" + version + "</version>\n"
            + "</project>\n"
    );
    Path jarPath = artifactDir.resolve(artifactId + "-" + version + ".jar");
    try (OutputStream os = Files.newOutputStream(jarPath);
         JarOutputStream ignored = new JarOutputStream(os)) {
      // Empty jar content is enough for resolution.
    }
    writeSha1File(pomPath);
    writeSha1File(jarPath);
  }

  private static File createMavenHomeWithLocalRepo(File localRepo) throws IOException {
    File mavenHome = Files.createTempDirectory("maven-home").toFile();
    Path confDir = mavenHome.toPath().resolve("conf");
    Files.createDirectories(confDir);
    Files.createDirectories(localRepo.toPath());
    String localRepoPath = localRepo.getAbsolutePath().replace("\\", "/");
    Files.writeString(
        confDir.resolve("settings.xml"),
        "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n"
            + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n"
            + "  <localRepository>" + localRepoPath + "</localRepository>\n"
            + "</settings>\n"
    );
    return mavenHome;
  }

  private static void createUserSettingsWithLocalRepo(File userHome, File localRepo) throws IOException {
    Path m2Dir = userHome.toPath().resolve(".m2");
    Files.createDirectories(m2Dir);
    Files.createDirectories(localRepo.toPath());
    String localRepoPath = localRepo.getAbsolutePath().replace("\\", "/");
    Files.writeString(
        m2Dir.resolve("settings.xml"),
        "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n"
            + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n"
            + "  <localRepository>" + localRepoPath + "</localRepository>\n"
            + "</settings>\n"
    );
  }

  private static void createSnapshotArtifactInRepository(File repoRoot, String groupId, String artifactId, String version,
                                                         String timestamp, String buildNumber) throws IOException {
    String baseVersion = version.substring(0, version.length() - "-SNAPSHOT".length());
    String resolvedVersion = baseVersion + "-" + timestamp + "-" + buildNumber;
    String updated = timestamp.replace(".", "");

    Path artifactRoot = repoRoot.toPath()
        .resolve(groupId.replace('.', File.separatorChar))
        .resolve(artifactId);
    Path snapshotDir = artifactRoot.resolve(version);
    Files.createDirectories(snapshotDir);

    Path artifactMetadataPath = artifactRoot.resolve("maven-metadata.xml");
    Files.writeString(
        artifactMetadataPath,
        "<metadata>\n"
            + "  <groupId>" + groupId + "</groupId>\n"
            + "  <artifactId>" + artifactId + "</artifactId>\n"
            + "  <versioning>\n"
            + "    <latest>" + version + "</latest>\n"
            + "    <versions>\n"
            + "      <version>" + version + "</version>\n"
            + "    </versions>\n"
            + "    <lastUpdated>" + updated + "</lastUpdated>\n"
            + "  </versioning>\n"
            + "</metadata>\n"
    );

    Path snapshotMetadataPath = snapshotDir.resolve("maven-metadata.xml");
    Files.writeString(
        snapshotMetadataPath,
        "<metadata modelVersion=\"1.1.0\">\n"
            + "  <groupId>" + groupId + "</groupId>\n"
            + "  <artifactId>" + artifactId + "</artifactId>\n"
            + "  <version>" + version + "</version>\n"
            + "  <versioning>\n"
            + "    <snapshot>\n"
            + "      <timestamp>" + timestamp + "</timestamp>\n"
            + "      <buildNumber>" + buildNumber + "</buildNumber>\n"
            + "    </snapshot>\n"
            + "    <lastUpdated>" + updated + "</lastUpdated>\n"
            + "    <snapshotVersions>\n"
            + "      <snapshotVersion>\n"
            + "        <extension>jar</extension>\n"
            + "        <value>" + resolvedVersion + "</value>\n"
            + "        <updated>" + updated + "</updated>\n"
            + "      </snapshotVersion>\n"
            + "      <snapshotVersion>\n"
            + "        <extension>pom</extension>\n"
            + "        <value>" + resolvedVersion + "</value>\n"
            + "        <updated>" + updated + "</updated>\n"
            + "      </snapshotVersion>\n"
            + "    </snapshotVersions>\n"
            + "  </versioning>\n"
            + "</metadata>\n"
    );

    Path snapshotPomPath = snapshotDir.resolve(artifactId + "-" + resolvedVersion + ".pom");
    Files.writeString(
        snapshotPomPath,
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>" + groupId + "</groupId>\n"
            + "  <artifactId>" + artifactId + "</artifactId>\n"
            + "  <version>" + version + "</version>\n"
            + "</project>\n"
    );
    Path snapshotJarPath = snapshotDir.resolve(artifactId + "-" + resolvedVersion + ".jar");
    try (OutputStream os = Files.newOutputStream(snapshotJarPath);
         JarOutputStream ignored = new JarOutputStream(os)) {
      // Empty jar content is enough for snapshot artifact resolution.
    }
    writeSha1File(artifactMetadataPath);
    writeSha1File(snapshotMetadataPath);
    writeSha1File(snapshotPomPath);
    writeSha1File(snapshotJarPath);
  }

  private static SystemPropertyOverride overrideSystemProperty(String key, String value) {
    return new SystemPropertyOverride(key, value);
  }

  private static void writeSha1File(Path file) throws IOException {
    try {
      byte[] data = Files.readAllBytes(file);
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] sha1 = digest.digest(data);
      StringBuilder hex = new StringBuilder(sha1.length * 2);
      for (byte b : sha1) {
        hex.append(String.format("%02x", b));
      }
      Files.writeString(file.resolveSibling(file.getFileName().toString() + ".sha1"), hex + "\n");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 algorithm not available", e);
    }
  }

  private static final class SystemPropertyOverride implements AutoCloseable {
    private final String key;
    private final String previousValue;

    private SystemPropertyOverride(String key, String value) {
      this.key = key;
      this.previousValue = System.getProperty(key);
      System.setProperty(key, value);
    }

    @Override
    public void close() {
      if (previousValue == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previousValue);
      }
    }
  }

  private static void createWrapper(File projectDir, String mavenHomeOutput, int exitCode) throws IOException {
    Path wrapperConfigDir = projectDir.toPath().resolve(".mvn/wrapper");
    Files.createDirectories(wrapperConfigDir);
    Files.writeString(wrapperConfigDir.resolve("maven-wrapper.properties"), "distributionUrl=https://example.invalid");

    Path unixWrapper = projectDir.toPath().resolve("mvnw");
    Files.writeString(
        unixWrapper,
        "#!/usr/bin/env bash\n"
            + "echo \"" + mavenHomeOutput + "\"\n"
            + "exit " + exitCode + "\n"
    );
    assertTrue(unixWrapper.toFile().setExecutable(true));

    Path windowsWrapper = projectDir.toPath().resolve("mvnw.cmd");
    Files.writeString(
        windowsWrapper,
        "@echo off\r\n"
            + "echo " + mavenHomeOutput + "\r\n"
            + "exit /b " + exitCode + "\r\n"
    );
  }
}

package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ClasspathResolutionTest {

  private static final Logger LOG = LoggerFactory.getLogger(ClasspathResolutionTest.class);

  @Test
  public void tablesawExcelResolvesTransitivePoiClasses() throws Exception {
    File pomFile = Paths.get(getClass().getResource("/pom/tablesaw_excel.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");

    List<String> fileNames = dependencies.stream().map(File::getName).sorted().collect(Collectors.toList());
    LOG.info("Resolved {} dependencies: {}", fileNames.size(), fileNames);

    // tablesaw-excel transitively depends on Apache POI
    assertTrue(fileNames.stream().anyMatch(f -> f.startsWith("poi-")),
        "Apache POI jar should be present as transitive dependency of tablesaw-excel");

    // Verify CellStyle is loadable from the resolved classpath
    ClassLoader cl = mavenUtils.getMavenDependenciesClassloader(pomFile, getClass().getClassLoader());
    Class<?> cellStyleClass = cl.loadClass("org.apache.poi.ss.usermodel.CellStyle");
    assertNotNull(cellStyleClass, "CellStyle class should be loadable from resolved classpath");
  }

  @Test
  public void onlyOneSlf4jApiVersionSelected() throws Exception {
    File pomFile = Paths.get(getClass().getResource("/pom/tablesaw_excel.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");

    List<String> slf4jApiJars = dependencies.stream()
        .map(File::getName)
        .filter(f -> f.startsWith("slf4j-api-"))
        .collect(Collectors.toList());

    LOG.info("slf4j-api jars found: {}", slf4jApiJars);
    assertEquals(1, slf4jApiJars.size(),
        "Exactly one slf4j-api version should be selected, but found: " + slf4jApiJars);
  }

  @Test
  public void runtimeAndTestScopesDifferCorrectly() throws Exception {
    File pomFile = Paths.get(getClass().getResource("/pom/tablesaw_excel.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();

    Set<File> runtimeDeps = mavenUtils.resolveDependencies(pomFile, false);
    Set<File> testDeps = mavenUtils.resolveDependencies(pomFile, true);

    List<String> runtimeNames = runtimeDeps.stream().map(File::getName).sorted().collect(Collectors.toList());
    List<String> testNames = testDeps.stream().map(File::getName).sorted().collect(Collectors.toList());

    LOG.info("Runtime classpath ({} jars): {}", runtimeNames.size(), runtimeNames);
    LOG.info("Test classpath ({} jars): {}", testNames.size(), testNames);

    // Test classpath should be a superset of runtime classpath
    assertTrue(testDeps.containsAll(runtimeDeps),
        "Test classpath should contain all runtime dependencies");

    // Test classpath should have more dependencies (junit-jupiter and its transitives)
    assertTrue(testDeps.size() > runtimeDeps.size(),
        "Test classpath (" + testDeps.size() + " jars) should have more dependencies than runtime ("
            + runtimeDeps.size() + " jars)");

    // JUnit should only appear in test classpath, not runtime
    assertTrue(testNames.stream().anyMatch(f -> f.contains("junit")),
        "Test classpath should contain junit jars");
    assertFalse(runtimeNames.stream().anyMatch(f -> f.contains("junit")),
        "Runtime classpath should not contain junit jars");
  }

  @Test
  public void candlesPomResolvesCompleteClasspath() throws Exception {
    File pomFile = Paths.get(getClass().getResource("/pom/candles.xml").toURI()).toFile();
    MavenUtils mavenUtils = new MavenUtils();
    Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
    assertNotNull(dependencies, "Failed to resolve dependencies");

    List<String> fileNames = dependencies.stream().map(File::getName).sorted().collect(Collectors.toList());
    LOG.info("Candles runtime classpath ({} jars): {}", fileNames.size(), fileNames);

    // Apache POI classes must be present (CellStyle for tablesaw-excel usage)
    ClassLoader cl = mavenUtils.getMavenDependenciesClassloader(pomFile, getClass().getClassLoader());
    Class<?> cellStyleClass = cl.loadClass("org.apache.poi.ss.usermodel.CellStyle");
    assertNotNull(cellStyleClass, "CellStyle class should be loadable");

    // tablesaw-core must be present (direct dependency)
    assertTrue(fileNames.stream().anyMatch(f -> f.startsWith("tablesaw-core-")),
        "tablesaw-core should be present as direct dependency");

    // Only one slf4j-api version (dependency mediation)
    List<String> slf4jApiJars = fileNames.stream()
        .filter(f -> f.startsWith("slf4j-api-"))
        .collect(Collectors.toList());
    assertEquals(1, slf4jApiJars.size(),
        "Exactly one slf4j-api version should be selected, but found: " + slf4jApiJars);

    // log4j-core should be present (runtime scope direct dependency)
    assertTrue(fileNames.stream().anyMatch(f -> f.startsWith("log4j-core-")),
        "log4j-core should be present as runtime dependency");
  }
}

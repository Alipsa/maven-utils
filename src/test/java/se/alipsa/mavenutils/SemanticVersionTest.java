package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SemanticVersionTest {

  @Test
  public void previousVersion() {
    assertTrue(SemanticVersion.compare("1", "2") < 0);
    assertTrue(SemanticVersion.compare("1.2.3", "1.2.4") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta1", "1.2.4-beta2") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta", "1.2.4-beta1") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta3", "1.2.4-GA") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta3", "1.2.4") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta3", "1.2.4-final") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta33", "1.2.4-GA") < 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta33", "1.2.4-GA") < 0);
  }

  @Test
  public void sameVersion() {
    assertEquals(0, SemanticVersion.compare("1", "1"));
    assertEquals(0, SemanticVersion.compare("1.2.4", "1.2.4"));
    assertEquals(0, SemanticVersion.compare("1.2.4-beta1", "1.2.4-beta1"));
    assertEquals(0, SemanticVersion.compare("1.2.4-beta", "1.2.4-beta"));
    assertEquals(0, SemanticVersion.compare("1.2.4-GA", "1.2.4-GA"));
    assertEquals(0, SemanticVersion.compare("1.2.9", "1.2.9-jdk11"));
  }

  @Test
  public void laterVersion() {
    assertTrue(SemanticVersion.compare("2", "1") > 0);
    assertTrue(SemanticVersion.compare("1.2.5", "1.2.4") > 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta3", "1.2.4-beta2") > 0);
    assertTrue(SemanticVersion.compare("1.2.4-beta1", "1.2.4-beta") > 0);
    assertTrue(SemanticVersion.compare("1.2.4-GA", "1.2.4-beta2") > 0);
    assertTrue(SemanticVersion.compare("1.2.40", "1.2.40-beta2") > 0);
    assertTrue(SemanticVersion.compare("1.2.40-beta22", "1.2.40-alpha92") > 0);
    assertTrue(SemanticVersion.compare("3.5.3", "0.9.2709") > 0);
  }
}

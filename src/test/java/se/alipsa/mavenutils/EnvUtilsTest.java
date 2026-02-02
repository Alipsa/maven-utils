package se.alipsa.mavenutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.Properties;

public class EnvUtilsTest {

  @Test
  public void parseArgumentsSupportsFlagsAndProperties() {
    Properties props = EnvUtils.parseArguments(new String[]{
        "-Dfoo=bar=baz",
        "-DskipTests",
        "-q",
        "-e",
        "-X",
        "-o",
        "-am",
        "-amd",
        "-U",
        "-Pabc,def",
        "-pl", "module-a,module-b",
        "-T4",
        "-rf", "module-c",
        "-s", "settings.xml",
        "-gsglobal.xml",
        "-t", "toolchains.xml",
        "-nsu",
        "-ntp"
    });

    assertEquals("bar=baz", props.getProperty("foo"));
    assertEquals("", props.getProperty("skipTests"));
    assertEquals("true", props.getProperty("quiet"));
    assertEquals("true", props.getProperty("errors"));
    assertEquals("true", props.getProperty("debug"));
    assertEquals("true", props.getProperty("offline"));
    assertEquals("true", props.getProperty("alsoMake"));
    assertEquals("true", props.getProperty("alsoMakeDependents"));
    assertEquals("true", props.getProperty("updateSnapshots"));
    assertEquals("abc,def", props.getProperty("profiles"));
    assertEquals("module-a,module-b", props.getProperty("projects"));
    assertEquals("4", props.getProperty("threads"));
    assertEquals("module-c", props.getProperty("resumeFrom"));
    assertEquals("settings.xml", props.getProperty("settings"));
    assertEquals("global.xml", props.getProperty("globalSettings"));
    assertEquals("toolchains.xml", props.getProperty("toolchains"));
    assertEquals("true", props.getProperty("noSnapshotUpdates"));
    assertEquals("true", props.getProperty("noTransferProgress"));
    assertNull(props.getProperty("unknownFlag"));
  }
}

package se.alipsa.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class EnvUtils {

  private static final Logger LOG = LoggerFactory.getLogger(EnvUtils.class);

  public static File getUserHome() {
    String userHome = System.getProperty("user.home");
    if (userHome == null) {
      userHome = System.getenv("user.home");
      if (userHome == null) {
        userHome = System.getenv("USERPROFILE");
        if (userHome == null) {
          userHome = System.getenv("HOME");
        }
      }
    }

    if (userHome == null) {
      LOG.error("Failed to find user home property");
      throw new NotFoundException("Failed to find user home property");
    }
    File homeDir = new File(userHome);
    if(!homeDir.exists()) {
      LOG.error("User home dir {} does not exist, something is not right", homeDir);
      throw new NotFoundException("User home dir " + homeDir + " does not exist, something is not right");
    }
    return homeDir;
  }
}
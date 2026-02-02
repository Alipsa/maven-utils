package se.alipsa.mavenutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

/**
 * Utility class for environment-related operations.
 * This class provides methods to retrieve the user's home directory
 * and parse command-line arguments into system properties.
 */
public class EnvUtils {

  private static final Logger LOG = LoggerFactory.getLogger(EnvUtils.class);

  private EnvUtils() {
    // private constructor to prevent instantiation
  }
  /**
   * A reasonably bulletproof way to get the user's home directory on any OS.
   * the .m2/settings.xml resides in the user's home dir so the primary purpose of this method
   * is to enable MavenUtils to parse the settings.xml file.
   * @return the users home directory.
   */
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

  /**
   * Parses command-line arguments to extract system properties.
   * Arguments should be in the format -Dkey=value.
   *
   * @param args the command-line arguments
   * @return a Properties object containing the parsed system properties
   */
  public static Properties parseArguments(String[] args) {
    Properties sysProps = new Properties();
    if (args == null) {
      return sysProps;
    }

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg == null || arg.isBlank()) {
        continue;
      }

      if (arg.startsWith("-D")) {
        String expression = arg.substring(2);
        int eqIdx = expression.indexOf('=');
        if (eqIdx < 0) {
          sysProps.setProperty(expression, "");
        } else {
          sysProps.setProperty(expression.substring(0, eqIdx), expression.substring(eqIdx + 1));
        }
        continue;
      }

      if ("-P".equals(arg) || arg.startsWith("-P")) {
        String value = consumeOptionValue(arg, args, i, 2);
        if (value != null) {
          sysProps.setProperty("profiles", value);
          if ("-P".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-pl".equals(arg) || arg.startsWith("-pl")) {
        String value = consumeOptionValue(arg, args, i, 3);
        if (value != null) {
          sysProps.setProperty("projects", value);
          if ("-pl".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-T".equals(arg) || arg.startsWith("-T")) {
        String value = consumeOptionValue(arg, args, i, 2);
        if (value != null) {
          sysProps.setProperty("threads", value);
          if ("-T".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-rf".equals(arg) || arg.startsWith("-rf")) {
        String value = consumeOptionValue(arg, args, i, 3);
        if (value != null) {
          sysProps.setProperty("resumeFrom", value);
          if ("-rf".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-s".equals(arg) || arg.startsWith("-s") || "--settings".equals(arg) || arg.startsWith("--settings")) {
        int prefixLength = arg.startsWith("--") ? 10 : 2;
        String value = consumeOptionValue(arg, args, i, prefixLength);
        if (value != null) {
          sysProps.setProperty("settings", value);
          if ("-s".equals(arg) || "--settings".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-gs".equals(arg) || arg.startsWith("-gs") || "--global-settings".equals(arg) || arg.startsWith("--global-settings")) {
        int prefixLength = arg.startsWith("--") ? 17 : 3;
        String value = consumeOptionValue(arg, args, i, prefixLength);
        if (value != null) {
          sysProps.setProperty("globalSettings", value);
          if ("-gs".equals(arg) || "--global-settings".equals(arg)) {
            i++;
          }
        }
        continue;
      }
      if ("-t".equals(arg) || arg.startsWith("-t") || "--toolchains".equals(arg) || arg.startsWith("--toolchains")) {
        int prefixLength = arg.startsWith("--") ? 12 : 2;
        String value = consumeOptionValue(arg, args, i, prefixLength);
        if (value != null) {
          sysProps.setProperty("toolchains", value);
          if ("-t".equals(arg) || "--toolchains".equals(arg)) {
            i++;
          }
        }
        continue;
      }

      switch (arg) {
        case "-q":
        case "--quiet":
          sysProps.setProperty("quiet", "true");
          continue;
        case "-e":
        case "--errors":
          sysProps.setProperty("errors", "true");
          continue;
        case "-X":
        case "--debug":
          sysProps.setProperty("debug", "true");
          continue;
        case "-o":
        case "--offline":
          sysProps.setProperty("offline", "true");
          continue;
        case "-am":
          sysProps.setProperty("alsoMake", "true");
          continue;
        case "-amd":
          sysProps.setProperty("alsoMakeDependents", "true");
          continue;
        case "-U":
          sysProps.setProperty("updateSnapshots", "true");
          continue;
        case "-nsu":
          sysProps.setProperty("noSnapshotUpdates", "true");
          continue;
        case "-ntp":
        case "--no-transfer-progress":
          sysProps.setProperty("noTransferProgress", "true");
          continue;
        case "-fae":
          sysProps.setProperty("reactorFailureBehavior", "failAtEnd");
          continue;
        case "-ff":
          sysProps.setProperty("reactorFailureBehavior", "failFast");
          continue;
        case "-fn":
          sysProps.setProperty("reactorFailureBehavior", "failNever");
          continue;
        default:
          // ignore unknown flags
      }
    }
    return sysProps;
  }

  private static String consumeOptionValue(String currentArg, String[] args, int index, int prefixLength) {
    if (currentArg.length() > prefixLength) {
      String value = currentArg.substring(prefixLength);
      if (value.startsWith("=")) {
        value = value.substring(1);
      }
      return value.isEmpty() ? null : value;
    }
    if (index + 1 < args.length) {
      return args[index + 1];
    }
    return null;
  }
}

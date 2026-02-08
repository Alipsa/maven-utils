package se.alipsa.mavenutils;

import static se.alipsa.mavenutils.EnvUtils.getUserHome;

import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.apache.maven.shared.invoker.*;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * High level api to use maven programmatically.
 * There are basically three different type of usages:
 * <ol>
 *   <li>Building (invoking) maven by using the runMaven static method</li>
 *   <li>Parsing the pom file to do various actions such as get the ClassLoader or resolve and get the Set of dependencies</li>
 *   <li>Get a particular artifact from the list of remote repositories (or from local if more recent) by using the resolveArtifact method</li>
 * </ol>
 */
public class MavenUtils {

  private static final Logger LOG = LoggerFactory.getLogger(MavenUtils.class);
  private static final String WRAPPER_UNIX = "mvnw";
  private static final String WRAPPER_WINDOWS = "mvnw.cmd";
  private static final String WRAPPER_PROPERTIES = ".mvn/wrapper/maven-wrapper.properties";
  private static final RemoteRepository CENTRAL_MAVEN_REPOSITORY = getCentralMavenRepository();
  private static final RemoteRepository BE_DATA_DRIVEN_MAVEN_REPOSITORY = getBeDataDrivenMavenRepository();

  private final List<RemoteRepository> remoteRepositories = new ArrayList<>();

  public enum MavenDistributionMode {
    WRAPPER,
    HOME,
    DEFAULT
  }

  public static final class MavenExecutionOptions {

    private final File projectDir;
    private final File configuredMavenHome;
    private final boolean preferWrapper;

    public MavenExecutionOptions() {
      this(null, null, true);
    }

    public MavenExecutionOptions(@Nullable File projectDir, @Nullable File configuredMavenHome, boolean preferWrapper) {
      this.projectDir = projectDir;
      this.configuredMavenHome = configuredMavenHome;
      this.preferWrapper = preferWrapper;
    }

    @Nullable
    public File getProjectDir() {
      return projectDir;
    }

    @Nullable
    public File getConfiguredMavenHome() {
      return configuredMavenHome;
    }

    public boolean isPreferWrapper() {
      return preferWrapper;
    }
  }

  public static final class MavenDistributionSelection {

    private final MavenDistributionMode mode;
    private final File projectDir;
    private final File mavenExecutable;
    private final File mavenHome;

    private MavenDistributionSelection(MavenDistributionMode mode, @Nullable File projectDir, @Nullable File mavenExecutable,
                                       @Nullable File mavenHome) {
      this.mode = mode;
      this.projectDir = projectDir;
      this.mavenExecutable = mavenExecutable;
      this.mavenHome = mavenHome;
    }

    public MavenDistributionMode getMode() {
      return mode;
    }

    @Nullable
    public File getProjectDir() {
      return projectDir;
    }

    @Nullable
    public File getMavenExecutable() {
      return mavenExecutable;
    }

    @Nullable
    public File getMavenHome() {
      return mavenHome;
    }
  }

  public static final class MavenRunResult {

    private final InvocationResult invocationResult;
    private final MavenDistributionSelection distributionSelection;

    private MavenRunResult(InvocationResult invocationResult, MavenDistributionSelection distributionSelection) {
      this.invocationResult = invocationResult;
      this.distributionSelection = distributionSelection;
    }

    public InvocationResult getInvocationResult() {
      return invocationResult;
    }

    public MavenDistributionSelection getDistributionSelection() {
      return distributionSelection;
    }
  }

  public static final class DependenciesResolutionResult {

    private final Set<File> dependencies;
    private final MavenDistributionSelection distributionSelection;

    private DependenciesResolutionResult(Set<File> dependencies, MavenDistributionSelection distributionSelection) {
      this.dependencies = Collections.unmodifiableSet(new HashSet<>(dependencies));
      this.distributionSelection = distributionSelection;
    }

    public Set<File> getDependencies() {
      return dependencies;
    }

    public MavenDistributionSelection getDistributionSelection() {
      return distributionSelection;
    }
  }

  /**
   * Default constructor, will use Maven Central remote repository
   */
  public MavenUtils() {
    remoteRepositories.addAll(getDefaultRemoteRepositories());
  }

  /**
   * A Remote repository is created as follows:
   * <code>
   * new org.eclipse.aether.repository.RemoteRepository
   *  .Builder("central", "default", "https://repo1.maven.org/maven2/")
   *  .build();
   * </code>
   *  The argument to Builder are String id, String type, String url.
   *  @see <a href="https://javadoc.io/static/org.eclipse.aether/aether-api/1.1.0/org/eclipse/aether/repository/RemoteRepository.Builder.html">
   *    org.eclipse.aether.repository.RemoteRepository.Builder</a>
   * @param remoteRepositories a list of RemoteRepositories to use for Maven pom operations.
   */
  public MavenUtils(List<RemoteRepository> remoteRepositories) {
    this.remoteRepositories.addAll(remoteRepositories);
  }

  /**
   * Get the default remote repositories used by MavenUtils
   * @return list of RemoteRepository
   */
  public static List<RemoteRepository> getDefaultRemoteRepositories() {
    return Arrays.asList(CENTRAL_MAVEN_REPOSITORY);
  }

  /**
   * Get the Maven Central remote repository
   * @return RemoteRepository for Maven Central
   */
  public static RemoteRepository getCentralMavenRepository() {
    return new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
        .build();
  }

  /**
   * Get the BeDataDriven Maven remote repository
   * @return RemoteRepository for BeDataDriven Maven repo
   */
  public static RemoteRepository getBeDataDrivenMavenRepository() {
   return new RemoteRepository.Builder("bedatadriven", "default", "https://nexus.bedatadriven.com/content/groups/public/")
        .build();
  }

  static InvocationRequest buildInvocationRequest(final File pomFile, String[] mvnArgs, @Nullable File javaHome) {
    ParsedMavenInvocation parsed = parseMavenArguments(mvnArgs);
    File dir = pomFile.getParentFile();
    InvocationRequest request = new DefaultInvocationRequest()
        .setBatchMode(true)
        .setPomFile(pomFile)
        .setBaseDirectory(dir)
        .setGoals(parsed.goals.isEmpty() ? Collections.emptyList() : parsed.goals);

    if (!parsed.properties.isEmpty()) {
      request.setProperties(parsed.properties);
    }
    if (!parsed.profiles.isEmpty()) {
      request.setProfiles(parsed.profiles);
    }
    if (!parsed.projects.isEmpty()) {
      request.setProjects(parsed.projects);
    }
    if (parsed.userSettingsFile != null) {
      request.setUserSettingsFile(parsed.userSettingsFile);
    }
    if (parsed.globalSettingsFile != null) {
      request.setGlobalSettingsFile(parsed.globalSettingsFile);
    }
    if (parsed.toolchainsFile != null) {
      request.setToolchainsFile(parsed.toolchainsFile);
    }
    if (parsed.threads != null) {
      request.setThreads(parsed.threads);
    }
    if (parsed.resumeFrom != null) {
      request.setResumeFrom(parsed.resumeFrom);
    }
    if (parsed.reactorFailureBehavior != null) {
      request.setReactorFailureBehavior(parsed.reactorFailureBehavior);
    }

    request.setAlsoMake(parsed.alsoMake);
    request.setAlsoMakeDependents(parsed.alsoMakeDependents);
    request.setOffline(parsed.offline);
    request.setUpdateSnapshots(parsed.updateSnapshots);
    request.setDebug(parsed.debug);
    request.setShowErrors(parsed.showErrors);
    request.setQuiet(parsed.quiet);
    request.setNoTransferProgress(parsed.noTransferProgress);

    if (javaHome != null) {
      request.setJavaHome(javaHome);
    }
    if (!parsed.additionalArgs.isEmpty()) {
      request.addArgs(parsed.additionalArgs);
    }
    return request;
  }

  /**
   * Run maven with the given arguments (targets) on the given pom file.
   *
   * @param pomFile the pom.xml file to parse
   * @param mvnArgs the arguments (targets) to send to maven (e.g. clean install)
   * @param consoleOutputHandler where normal maven output will be sent, defaults to System.out
   * @param warningOutputHandler where maven warning outputs will be sent, defaults to System.err
   * @return InvocationResult the result of running the targets
   * @throws MavenInvocationException if there is a problem with parsing or running maven
   */
  public static InvocationResult runMaven(final File pomFile, String[] mvnArgs,
                                          @Nullable InvocationOutputHandler consoleOutputHandler,
                                          @Nullable InvocationOutputHandler warningOutputHandler) throws MavenInvocationException {
    return runMaven(pomFile, mvnArgs, null, null, consoleOutputHandler, warningOutputHandler);
  }

  /**
   * Run maven with the given arguments (targets) on the given pom file.
   *
   * @param pomFile the pom.xml file to parse
   * @param mvnArgs the arguments (targets) to send to maven (e.g. clean install)
   * @param options invocation options controlling wrapper/home/default selection
   * @param consoleOutputHandler where normal maven output will be sent, defaults to System.out
   * @param warningOutputHandler where maven warning outputs will be sent, defaults to System.err
   * @return InvocationResult the result of running the targets
   * @throws MavenInvocationException if there is a problem with parsing or running maven
   */
  public static InvocationResult runMaven(final File pomFile, String[] mvnArgs,
                                          @Nullable MavenExecutionOptions options,
                                          @Nullable InvocationOutputHandler consoleOutputHandler,
                                          @Nullable InvocationOutputHandler warningOutputHandler) throws MavenInvocationException {
    return runMaven(pomFile, mvnArgs, null, options, consoleOutputHandler, warningOutputHandler);
  }

  /**
   * Run maven with the given arguments (targets) on the given pom file using a specific Java home.
   *
   * @param pomFile the pom.xml file to parse
   * @param mvnArgs the arguments (targets) to send to maven (e.g. clean install)
   * @param javaHome the Java home to use for this invocation, or null to use the default
   * @param consoleOutputHandler where normal maven output will be sent, defaults to System.out
   * @param warningOutputHandler where maven warning outputs will be sent, defaults to System.err
   * @return InvocationResult the result of running the targets
   * @throws MavenInvocationException if there is a problem with parsing or running maven
   */
  public static InvocationResult runMaven(final File pomFile, String[] mvnArgs,
                                          @Nullable File javaHome,
                                          @Nullable InvocationOutputHandler consoleOutputHandler,
                                          @Nullable InvocationOutputHandler warningOutputHandler) throws MavenInvocationException {
    return runMaven(pomFile, mvnArgs, javaHome, null, consoleOutputHandler, warningOutputHandler);
  }

  /**
   * Run maven with the given arguments (targets) on the given pom file using per-invocation options.
   *
   * @param pomFile the pom.xml file to parse
   * @param mvnArgs the arguments (targets) to send to maven (e.g. clean install)
   * @param javaHome the Java home to use for this invocation, or null to use the default
   * @param options invocation options controlling wrapper/home/default selection
   * @param consoleOutputHandler where normal maven output will be sent, defaults to System.out
   * @param warningOutputHandler where maven warning outputs will be sent, defaults to System.err
   * @return InvocationResult the result of running the targets
   * @throws MavenInvocationException if there is a problem with parsing or running maven
   */
  public static InvocationResult runMaven(final File pomFile, String[] mvnArgs,
                                          @Nullable File javaHome,
                                          @Nullable MavenExecutionOptions options,
                                          @Nullable InvocationOutputHandler consoleOutputHandler,
                                          @Nullable InvocationOutputHandler warningOutputHandler) throws MavenInvocationException {
    return runMavenWithSelection(pomFile, mvnArgs, javaHome, options, consoleOutputHandler, warningOutputHandler)
        .getInvocationResult();
  }

  /**
   * Run maven and return both invocation result and selected maven distribution metadata.
   */
  public static MavenRunResult runMavenWithSelection(final File pomFile, String[] mvnArgs,
                                                     @Nullable File javaHome,
                                                     @Nullable MavenExecutionOptions options,
                                                     @Nullable InvocationOutputHandler consoleOutputHandler,
                                                     @Nullable InvocationOutputHandler warningOutputHandler)
      throws MavenInvocationException {
    InvocationRequest request = buildInvocationRequest(pomFile, mvnArgs, javaHome);
    MavenDistributionSelection selection = selectMavenDistribution(pomFile, options);
    LOG.info("Running maven from dir {} with goals {} and args {} using {} mode",
        request.getBaseDirectory(), request.getGoals(), request.getArgs(), selection.getMode());
    Invoker invoker = new DefaultInvoker();
    configureInvoker(invoker, selection);
    request.setOutputHandler(consoleOutputHandler == null ? new ConsoleInvocationOutputHandler() : consoleOutputHandler);
    request.setErrorHandler(warningOutputHandler == null ? new WarningInvocationOutputHandler() : warningOutputHandler);
    return new MavenRunResult(invoker.execute(request), selection);
  }

  /**
   * Run maven with the given arguments, allowing per-invocation Java home override.
   * This is a convenience method that returns the exit code directly and uses Consumer-based output handlers.
   * <p>
   * <b>Note:</b> If the compiler reports method ambiguity, explicitly type the Consumer variables:
   * <pre>{@code
   * java.util.function.Consumer<String> outConsumer = System.out::println;
   * java.util.function.Consumer<String> errConsumer = System.err::println;
   * int exitCode = MavenUtils.runMaven(pomFile, mvnArgs, javaHome, outConsumer, errConsumer);
   * }</pre>
   * This is needed because InvocationOutputHandler is a functional interface with a compatible signature.
   *
   * @param pomFile the pom.xml file to parse
   * @param mvnArgs the arguments (targets) to send to maven (e.g. clean install)
   * @param javaHome the Java home to use for this invocation (required)
   * @param outConsumer consumer for standard output lines, can be null
   * @param errConsumer consumer for error output lines, can be null
   * @return the Maven exit code (0 for success, non-zero for failure)
   * @throws MavenInvocationException if there is a problem with parsing or running maven
   */
  public static int runMaven(final File pomFile, String[] mvnArgs, File javaHome,
                             @Nullable java.util.function.Consumer<String> outConsumer,
                             @Nullable java.util.function.Consumer<String> errConsumer) throws MavenInvocationException {
    return runMaven(pomFile, mvnArgs, javaHome, null, outConsumer, errConsumer);
  }

  /**
   * Run maven with the given arguments and option-based maven distribution selection.
   */
  public static int runMaven(final File pomFile, String[] mvnArgs, File javaHome,
                             @Nullable MavenExecutionOptions options,
                             @Nullable java.util.function.Consumer<String> outConsumer,
                             @Nullable java.util.function.Consumer<String> errConsumer) throws MavenInvocationException {
    InvocationOutputHandler consoleHandler = outConsumer != null ? outConsumer::accept : null;
    InvocationOutputHandler errorHandler = errConsumer != null ? errConsumer::accept : null;
    InvocationResult result = runMaven(pomFile, mvnArgs, javaHome, options, consoleHandler, errorHandler);
    return result.getExitCode();
  }

  private static ParsedMavenInvocation parseMavenArguments(String[] mvnArgs) {
    ParsedMavenInvocation parsed = new ParsedMavenInvocation();
    if (mvnArgs == null) {
      return parsed;
    }
    for (int i = 0; i < mvnArgs.length; i++) {
      String arg = mvnArgs[i];
      if (arg == null || arg.isBlank()) {
        continue;
      }
      if (arg.startsWith("-D")) {
        parsed.addProperty(arg.substring(2));
        continue;
      }
      if ("-P".equals(arg) || arg.startsWith("-P")) {
        String value = consumeOptionValue(arg, mvnArgs, i, 2);
        if (value != null) {
          parsed.addProfiles(value);
          if ("-P".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-s".equals(arg) || arg.startsWith("-s") || "--settings".equals(arg) || arg.startsWith("--settings")) {
        int prefixLength = arg.startsWith("--") ? 10 : 2;
        String value = consumeOptionValue(arg, mvnArgs, i, prefixLength);
        if (value != null) {
          parsed.userSettingsFile = new File(value);
          if ("-s".equals(arg) || "--settings".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-gs".equals(arg) || arg.startsWith("-gs") || "--global-settings".equals(arg) || arg.startsWith("--global-settings")) {
        int prefixLength = arg.startsWith("--") ? 17 : 3;
        String value = consumeOptionValue(arg, mvnArgs, i, prefixLength);
        if (value != null) {
          parsed.globalSettingsFile = new File(value);
          if ("-gs".equals(arg) || "--global-settings".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-t".equals(arg) || arg.startsWith("-t") || "--toolchains".equals(arg) || arg.startsWith("--toolchains")) {
        int prefixLength = arg.startsWith("--") ? 12 : 2;
        String value = consumeOptionValue(arg, mvnArgs, i, prefixLength);
        if (value != null) {
          parsed.toolchainsFile = new File(value);
          if ("-t".equals(arg) || "--toolchains".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-pl".equals(arg) || arg.startsWith("-pl")) {
        String value = consumeOptionValue(arg, mvnArgs, i, 3);
        if (value != null) {
          parsed.addProjects(value);
          if ("-pl".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-rf".equals(arg) || arg.startsWith("-rf")) {
        String value = consumeOptionValue(arg, mvnArgs, i, 3);
        if (value != null) {
          parsed.resumeFrom = value;
          if ("-rf".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      if ("-T".equals(arg) || arg.startsWith("-T")) {
        String value = consumeOptionValue(arg, mvnArgs, i, 2);
        if (value != null) {
          parsed.threads = value;
          if ("-T".equals(arg)) {
            i++;
          }
        } else {
          parsed.additionalArgs.add(arg);
        }
        continue;
      }
      switch (arg) {
        case "-am":
          parsed.alsoMake = true;
          continue;
        case "-amd":
          parsed.alsoMakeDependents = true;
          continue;
        case "-U":
          parsed.updateSnapshots = true;
          continue;
        case "-o":
        case "--offline":
          parsed.offline = true;
          continue;
        case "-q":
        case "--quiet":
          parsed.quiet = true;
          continue;
        case "-X":
        case "--debug":
          parsed.debug = true;
          continue;
        case "-e":
        case "--errors":
          parsed.showErrors = true;
          continue;
        case "-ntp":
        case "--no-transfer-progress":
          parsed.noTransferProgress = true;
          continue;
        case "-fae":
          parsed.reactorFailureBehavior = InvocationRequest.ReactorFailureBehavior.FailAtEnd;
          continue;
        case "-ff":
          parsed.reactorFailureBehavior = InvocationRequest.ReactorFailureBehavior.FailFast;
          continue;
        case "-fn":
          parsed.reactorFailureBehavior = InvocationRequest.ReactorFailureBehavior.FailNever;
          continue;
        default:
          if (arg.startsWith("-")) {
            parsed.additionalArgs.add(arg);
          } else {
            parsed.goals.add(arg);
          }
      }
    }
    return parsed;
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

  private static final class ParsedMavenInvocation {
    private final List<String> goals = new ArrayList<>();
    private final Properties properties = new Properties();
    private final List<String> profiles = new ArrayList<>();
    private final List<String> projects = new ArrayList<>();
    private final List<String> additionalArgs = new ArrayList<>();
    private boolean alsoMake;
    private boolean alsoMakeDependents;
    private boolean offline;
    private boolean updateSnapshots;
    private boolean debug;
    private boolean showErrors;
    private boolean quiet;
    private boolean noTransferProgress;
    private String threads;
    private String resumeFrom;
    private InvocationRequest.ReactorFailureBehavior reactorFailureBehavior;
    private File userSettingsFile;
    private File globalSettingsFile;
    private File toolchainsFile;

    private void addProperty(String expression) {
      int equalsIndex = expression.indexOf('=');
      if (equalsIndex < 0) {
        properties.setProperty(expression, "");
      } else {
        properties.setProperty(expression.substring(0, equalsIndex), expression.substring(equalsIndex + 1));
      }
    }

    private void addProfiles(String profilesArg) {
      if (profilesArg == null || profilesArg.isBlank()) {
        return;
      }
      Arrays.stream(profilesArg.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(profiles::add);
    }

    private void addProjects(String projectsArg) {
      if (projectsArg == null || projectsArg.isBlank()) {
        return;
      }
      Arrays.stream(projectsArg.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(projects::add);
    }
  }

  private static void configureInvoker(Invoker invoker, MavenDistributionSelection selection) {
    if (selection.getMode() == MavenDistributionMode.WRAPPER) {
      File wrapperExecutable = selection.getMavenExecutable();
      if (wrapperExecutable != null && wrapperExecutable.isFile()) {
        LOG.debug("Using Maven wrapper executable {}", wrapperExecutable.getAbsolutePath());
        invoker.setMavenExecutable(wrapperExecutable);
      } else {
        LOG.warn("Wrapper mode selected but wrapper executable is missing: {}", wrapperExecutable);
      }
      return;
    }
    File mavenHome = selection.getMavenHome();
    if (mavenHome != null && mavenHome.exists()) {
      LOG.debug("MAVEN_HOME used is {}", mavenHome.getAbsolutePath());
      invoker.setMavenHome(mavenHome);
    } else {
      // Without maven home, only a small subset of maven commands will work
      LOG.warn("No MAVEN_HOME set or set to a non-existing maven home: {}, this might not go well...", mavenHome);
    }
  }

  static MavenDistributionSelection selectMavenDistribution(@Nullable File pomFile, @Nullable MavenExecutionOptions options) {
    MavenExecutionOptions effectiveOptions = options == null ? new MavenExecutionOptions() : options;
    File projectDir = effectiveOptions.getProjectDir();
    if (projectDir == null && pomFile != null) {
      projectDir = pomFile.getParentFile();
    }
    if (effectiveOptions.isPreferWrapper()) {
      File wrapperExecutable = findWrapperExecutable(projectDir);
      if (wrapperExecutable != null) {
        return new MavenDistributionSelection(MavenDistributionMode.WRAPPER, projectDir, wrapperExecutable, null);
      }
    }
    File configuredMavenHome = effectiveOptions.getConfiguredMavenHome();
    if (configuredMavenHome != null) {
      return new MavenDistributionSelection(MavenDistributionMode.HOME, projectDir, null, configuredMavenHome);
    }
    String locatedMavenHome = locateMavenHome();
    File defaultMavenHome = locatedMavenHome == null || locatedMavenHome.isBlank() ? null : new File(locatedMavenHome);
    return new MavenDistributionSelection(MavenDistributionMode.DEFAULT, projectDir, null, defaultMavenHome);
  }

  @Nullable
  static File findWrapperExecutable(@Nullable File projectDir) {
    if (projectDir == null || !projectDir.isDirectory()) {
      return null;
    }
    File wrapperProperties = new File(projectDir, WRAPPER_PROPERTIES);
    if (!wrapperProperties.isFile()) {
      return null;
    }
    File unixWrapper = new File(projectDir, WRAPPER_UNIX);
    File windowsWrapper = new File(projectDir, WRAPPER_WINDOWS);
    if (!unixWrapper.isFile() && !windowsWrapper.isFile()) {
      return null;
    }
    if (isWindows()) {
      return windowsWrapper.isFile() ? windowsWrapper : unixWrapper;
    }
    return unixWrapper.isFile() ? unixWrapper : windowsWrapper;
  }

  @Nullable
  private static File resolveMavenHomeForSettings(MavenDistributionSelection selection) {
    if (selection.getMode() == MavenDistributionMode.WRAPPER) {
      File wrapperExecutable = selection.getMavenExecutable();
      if (wrapperExecutable == null) {
        return null;
      }
      String wrapperMavenHome = resolveMavenHomeFromExecutable(wrapperExecutable);
      if (wrapperMavenHome == null || wrapperMavenHome.isBlank()) {
        return null;
      }
      return new File(wrapperMavenHome);
    }
    return selection.getMavenHome();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  /**
   * Locates the MAVEN_HOME either from system property or environment variable.
   * If not found it will try to locate it from the PATH environment variable.
   *
   * @return the MAVEN_HOME path
   */
  public static String locateMavenHome() {
    String mavenHome = System.getProperty("MAVEN_HOME", System.getenv("MAVEN_HOME"));
    if (mavenHome == null) {
      mavenHome = locateMaven();
    }
    return mavenHome;
  }

  /**
   * Get a ClassLoader that includes the dependencies defined in the given pom file.
   *
   * @param pomFile the pom.xml file to parse
   * @param possibleParent an optional parent ClassLoader, can be null
   * @return a ClassLoader that includes the dependencies defined in the pom file
   * @throws Exception if there was some issue parsing the pom file or resolving dependencies
   */
  public ClassLoader getMavenDependenciesClassloader(File pomFile, @Nullable ClassLoader possibleParent) throws Exception {
    return getMavenClassLoader(parsePom(pomFile), resolveDependencies(pomFile), possibleParent);
  }

  /**
   * Resolve an artifact from the remote repositories.
   *
   * @param groupId is the same as the &lt;groupId&gt; tag in the pom.xml
   * @param artifactId is the same as the &lt;artifactId&gt; tag in the pom.xml
   * @param version is the same as the &lt;version&gt; tag in the pom.xml
   * @return a file pointing to the resolved artifact.
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ArtifactResolutionException if the artifact does not exist (e.g arguments are wrong) or some transport issue
   */
  public File resolveArtifact(String groupId, String artifactId, String version) throws SettingsBuildingException, ArtifactResolutionException {
    return resolveArtifact(groupId, artifactId, null, "jar", version);
  }
  /**
   * Resolve an artifact from the remote repositories.
   *
   * @param groupId is the same as the &lt;groupId&gt; tag in the pom.xml
   * @param artifactId is the same as the &lt;artifactId&gt; tag in the pom.xml
   * @param classifier is typically null, javadoc, sources, dist etc
   * @param extension could be pom, jar, zip etc.
   * @param version is the same as the &lt;version&gt; tag in the pom.xml
   * @return a file pointing to the resolved artifact.
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ArtifactResolutionException if the artifact does not exist (e.g arguments are wrong) or some transport issue
   */
  public File resolveArtifact(String groupId, String artifactId, String classifier, String extension, String version) throws SettingsBuildingException, ArtifactResolutionException {
    Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    artifactRequest.setRepositories(remoteRepositories);
    RepositorySystem repositorySystem = getRepositorySystem();
    RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);

    try {
      ArtifactResult artifactResult = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
      Artifact fetchedArtifact = artifactResult.getArtifact();
      if (fetchedArtifact != null){
        return fetchedArtifact.getFile();
      }
    } catch (ArtifactResolutionException e) {
      LOG.warn("Failed to find artifact in remote repos: {}; groupId={}, artifact = {}, classifier = {}, extension = {}, version = {}",
          e, groupId, artifactId, classifier, extension, version);
      throw e;
    }
    return null;
  }

  /**
   * Resolve the dependencies for the given pom file.
   *
   * @param pomFile the pom.xml file to parse
   * @param includeTestScope if true test scope dependencies will be included
   * @return a Set of Files representing the resolved dependencies
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ModelBuildingException if there was some issue with the pom file
   * @throws DependenciesResolveException if there was some issue resolving dependencies
   */
  public Set<File> resolveDependencies(File pomFile, boolean... includeTestScope) throws SettingsBuildingException, ModelBuildingException,
      DependenciesResolveException {
    return resolveDependencies(pomFile, null, includeTestScope);
  }

  /**
   * Resolve dependencies for the given pom file using per-invocation options.
   *
   * @param pomFile the pom.xml file to parse
   * @param options invocation options controlling wrapper/home/default selection
   * @param includeTestScope if true test scope dependencies will be included
   * @return a Set of Files representing the resolved dependencies
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ModelBuildingException if there was some issue with the pom file
   * @throws DependenciesResolveException if there was some issue resolving dependencies
   */
  public Set<File> resolveDependencies(File pomFile, @Nullable MavenExecutionOptions options, boolean... includeTestScope)
      throws SettingsBuildingException, ModelBuildingException, DependenciesResolveException {
    return resolveDependenciesWithSelection(pomFile, options, includeTestScope).getDependencies();
  }

  /**
   * Resolve dependencies and return both resolved artifacts and selected maven distribution metadata.
   * <p>
   * Uses the Aether {@link org.eclipse.aether.collection.CollectRequest} /
   * {@link org.eclipse.aether.resolution.DependencyRequest} API with a
   * {@link BomAwareRepositorySystemSupplier}-backed {@link RepositorySystem} to resolve the
   * full transitive dependency graph. The effective POM model is obtained via {@link #parsePom(File)}
   * which handles property interpolation, parent inheritance, and BOM resolution.
   * </p>
   */
  public DependenciesResolutionResult resolveDependenciesWithSelection(File pomFile, @Nullable MavenExecutionOptions options,
                                                                      boolean... includeTestScope)
      throws SettingsBuildingException, ModelBuildingException, DependenciesResolveException {
    boolean testScope = includeTestScope.length > 0 && includeTestScope[0];
    File mavenHome = options != null ? options.getConfiguredMavenHome() : null;

    Model model = parsePom(pomFile, mavenHome);

    RepositorySystem repositorySystem = getRepositorySystem();
    DefaultRepositorySystemSession session = getRepositorySystemSession(repositorySystem, mavenHome);

    // Collect remote repositories from both this instance and the POM model
    List<RemoteRepository> repos = new ArrayList<>(remoteRepositories);
    for (RemoteRepository modelRepo : getRepositories(model)) {
      if (repos.stream().noneMatch(r -> r.getId().equals(modelRepo.getId()))) {
        repos.add(modelRepo);
      }
    }

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRepositories(repos);

    for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
      String scope = dep.getScope() == null ? JavaScopes.COMPILE : dep.getScope();
      org.eclipse.aether.graph.Dependency aetherDep = new org.eclipse.aether.graph.Dependency(
          new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(),
              dep.getClassifier(), dep.getType() == null ? "jar" : dep.getType(), dep.getVersion()),
          scope
      );

      // Apply exclusions from the model
      if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {
        Collection<Exclusion> exclusions = new ArrayList<>();
        for (org.apache.maven.model.Exclusion excl : dep.getExclusions()) {
          exclusions.add(new Exclusion(excl.getGroupId(), excl.getArtifactId(), "*", "*"));
        }
        aetherDep = aetherDep.setExclusions(exclusions);
      }

      collectRequest.addDependency(aetherDep);
    }

    String filterScope = testScope ? JavaScopes.TEST : JavaScopes.RUNTIME;
    DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(filterScope);
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

    Set<File> dependencies = new LinkedHashSet<>();
    try {
      DependencyResult result = repositorySystem.resolveDependencies(session, dependencyRequest);
      for (ArtifactResult artifactResult : result.getArtifactResults()) {
        Artifact artifact = artifactResult.getArtifact();
        if (artifact != null && artifact.getFile() != null) {
          dependencies.add(artifact.getFile());
        }
      }
    } catch (org.eclipse.aether.resolution.DependencyResolutionException e) {
      throw new DependenciesResolveException("Failed to resolve dependencies for " + pomFile, e);
    }

    MavenDistributionSelection selection = selectMavenDistribution(pomFile, options);
    return new DependenciesResolutionResult(dependencies, selection);
  }

  /**
   * Parse the given POM file and return its effective model.
   * <p>
   * This method uses a {@link ModelResolver} initialized with the repositories configured in the
   * {@link MavenUtils} constructor (typically Maven Central plus any custom repositories).
   * During model building, Maven's {@link org.apache.maven.model.building.DefaultModelBuilder}
   * automatically calls {@link ModelResolver#addRepository(Repository)} when it encounters
   * {@code <repositories>} sections in the POM or its parent POMs. These discovered repositories
   * are aggregated with the initial repositories, making them available for parent POM resolution
   * and dependency resolution.
   * </p>
   *
   * @param pomFile the pom.xml file to parse
   * @return a Model (i.e. the Maven object representation of the effective pom file)
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ModelBuildingException if there was some issue with the pom file
   */
  public Model parsePom(File pomFile) throws SettingsBuildingException, ModelBuildingException {
    return parsePom(pomFile, null);
  }

  private Model parsePom(File pomFile, @Nullable File mavenHome) throws SettingsBuildingException, ModelBuildingException {
    final DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()
       .setPomFile(pomFile);
    RepositorySystem repositorySystem = getRepositorySystem();
    RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem, mavenHome);
    modelBuildingRequest.setModelResolver(new ModelResolver(
        remoteRepositories,
        repositorySystemSession,
        repositorySystem
        )
    );
    modelBuildingRequest.setSystemProperties(System.getProperties());

    ModelBuilder modelBuilder = new ParentPomsAsDependencyModelBuilder();
    ModelBuildingResult modelBuildingResult = modelBuilder.build(modelBuildingRequest);

    return modelBuildingResult.getEffectiveModel();
  }

  private static String locateMaven() {
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) {
      return "";
    }
    String[] pathElements = path.split(File.pathSeparator);
    for (String elem : pathElements) {
      File dir = new File(elem);
      if (!dir.isDirectory()) {
        continue;
      }
      File mvn = new File(dir, "mvn");
      if (mvn.canExecute()) {
        String home = resolveMavenHomeFromExecutable(mvn);
        if (!home.isEmpty()) {
          return home;
        }
      }
      File mvnCmd = new File(dir, "mvn.cmd");
      if (mvnCmd.canExecute()) {
        String home = resolveMavenHomeFromExecutable(mvnCmd);
        if (!home.isEmpty()) {
          return home;
        }
      }
      File mvnBat = new File(dir, "mvn.bat");
      if (mvnBat.canExecute()) {
        String home = resolveMavenHomeFromExecutable(mvnBat);
        if (!home.isEmpty()) {
          return home;
        }
      }
    }
    return "";
  }

  static String resolveMavenHomeFromExecutable(File mvnExecutable) {
    ProcessBuilder pb = new ProcessBuilder(
        mvnExecutable.getAbsolutePath(),
        "help:evaluate",
        "-Dexpression=maven.home",
        "-q",
        "-DforceStdout"
    );
    pb.redirectErrorStream(true);
    try {
      Process process = pb.start();
      String output;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        output = reader.lines()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .findFirst()
            .orElse("");
      }
      int exit = process.waitFor();
      if (exit == 0 && !output.isBlank()) {
        return output;
      }
      LOG.warn("Failed to evaluate maven.home using {} (exit code {}), output: {}", mvnExecutable, exit, output);
    } catch (IOException e) {
      LOG.warn("Failed to execute {} to determine Maven home: {}", mvnExecutable, e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while resolving Maven home using {}", mvnExecutable);
    }
    return "";
  }

  static RepositorySystem getRepositorySystem() {
    return new BomAwareRepositorySystemSupplier().get();
  }

  static DefaultRepositorySystemSession getRepositorySystemSession(RepositorySystem system) throws SettingsBuildingException {
    return getRepositorySystemSession(system, null);
  }

  static DefaultRepositorySystemSession getRepositorySystemSession(RepositorySystem system, @Nullable File mavenHome) throws SettingsBuildingException {
    DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = getLocalRepository(mavenHome);
    repositorySystemSession.setLocalRepositoryManager(
       system.newLocalRepositoryManager(repositorySystemSession, localRepository));

    repositorySystemSession.setRepositoryListener(new ConsoleRepositoryEventListener());

    // Propagate JVM system properties so that profile activation (e.g. jdk9+) and
    // property interpolation work correctly in the ArtifactDescriptorReader's ModelBuilder
    repositorySystemSession.setSystemProperties(System.getProperties());

    return repositorySystemSession;
  }

  /**
   * Get the local repository as defined in the user's settings.xml or default to ~/.m2/repository
   * @return LocalRepository instance pointing to the local maven repository
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   */
  public static LocalRepository getLocalRepository() throws SettingsBuildingException {
    return getLocalRepository(null);
  }

  static LocalRepository getLocalRepository(@Nullable File mavenHome) throws SettingsBuildingException {
    Settings settings = getSettings(mavenHome);
    String localRepoPath = settings.getLocalRepository();

    if (localRepoPath != null) {
      localRepoPath = localRepoPath.replace("${user.home}", EnvUtils.getUserHome().getAbsolutePath());
    } else {
      localRepoPath = new File(EnvUtils.getUserHome(), ".m2/repository").getAbsolutePath();
    }
    return new LocalRepository(localRepoPath);
  }

  private static Settings getSettings() throws SettingsBuildingException {
    return getSettings(null);
  }

  private static Settings getSettings(@Nullable File mavenHome) throws SettingsBuildingException {
    DefaultSettingsReader settingsReader = new DefaultSettingsReader();
    DefaultSettingsWriter settingsWriter = new DefaultSettingsWriter();
    DefaultSettingsValidator settingsValidator = new DefaultSettingsValidator();
    DefaultSettingsBuilder defaultSettingsBuilder = new DefaultSettingsBuilder(settingsReader, settingsWriter, settingsValidator);
    DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
    File userSettingsFile = new File(getUserHome(), ".m2/settings.xml");
    if (userSettingsFile.exists()) {
      request.setUserSettingsFile(userSettingsFile);
    } else {
      LOG.warn("Did not find a settings.xml in {}", userSettingsFile.getAbsolutePath() );
    }
    File effectiveMavenHome = mavenHome;
    if (effectiveMavenHome == null) {
      String m2Home = System.getenv("M2_HOME") != null ? System.getenv("M2_HOME") : System.getenv("MAVEN_HOME");
      if (m2Home != null && !m2Home.isBlank()) {
        effectiveMavenHome = new File(m2Home);
      }
    }
    if (effectiveMavenHome != null) {
      File globalSettingsFile = new File(effectiveMavenHome, "conf/settings.xml");
      if (globalSettingsFile.exists()) {
        request.setGlobalSettingsFile(globalSettingsFile);
      }
    }

    defaultSettingsBuilder.setSettingsWriter(new DefaultSettingsWriter());
    defaultSettingsBuilder.setSettingsReader(new DefaultSettingsReader());
    defaultSettingsBuilder.setSettingsValidator(new DefaultSettingsValidator());
    SettingsBuildingResult build = defaultSettingsBuilder.build(request);
    return build.getEffectiveSettings();
  }

  private static ClassLoader getMavenClassLoader(Model project, Collection<File> dependencies, ClassLoader parent) throws Exception {
    List<String> classpathElements = getClassPathElements(project);
    List<URL> urls = new ArrayList<>();
    for (String elem : classpathElements) {
      if (elem == null) {
        continue;
      }
      URL url = new File(elem).toURI().toURL();
      urls.add(url);
      LOG.debug("Adding {} to classloader", url);
    }

    for (File dep : dependencies) {
      if (dep != null && dep.exists()) {
        URL url = dep.toURI().toURL();
        urls.add(url);
        LOG.debug("Adding {} to classloader", url);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), parent);
  }

  private static List<RemoteRepository> getRepositories(Model model) {
    List<RemoteRepository> repos = new ArrayList<>();
    model.getRepositories().forEach(r ->
       repos.add(new RemoteRepository.Builder(r.getId(), r.getLayout(), r.getUrl()).build()));

    Iterator<RemoteRepository> it = repos.iterator();
    boolean addCentral = true;
    while (it.hasNext()) {
      RemoteRepository repo = it.next();
      // a non longer valid url for central, so we deal with that below
      if (repo.getUrl().equals("http://repo.maven.apache.org/maven2")) {
        it.remove();
      }
      if (repo.getId().equals(CENTRAL_MAVEN_REPOSITORY.getId()) || repo.getUrl().contains(".maven.org/maven2")) {
        addCentral = false;
      }
    }
    // Add a good central url in case the pom did not have it, the invalid model one was removed
    if (addCentral) {
      repos.add(CENTRAL_MAVEN_REPOSITORY);
    }
    return repos;
  }

  private static List<String> getClassPathElements(Model project) {
    List<String> classpathElements = new ArrayList<>();
    classpathElements.add(project.getBuild().getOutputDirectory());
    classpathElements.add(project.getBuild().getTestOutputDirectory());
    return classpathElements;
  }

  /**
   * Add a remote repository to this MavenUtils instance.
   *
   * @param id the id of the remote repository
   * @param url the url of the remote repository
   * @return this MavenUtils instance
   */
  public MavenUtils addRemoteRepository(String id, String url) {
    return addRemoteRepository(id, "default", url);
  }

  /**
   * Add a remote repository to this MavenUtils instance.
   *
   * @param id the id of the remote repository
   * @param type the type of the remote repository
   * @param url the url of the remote repository
   * @return this MavenUtils instance
   */
  public MavenUtils addRemoteRepository(String id, String type, String url) {
    return addRemoteRepository(new RemoteRepository.Builder(id, type, url)
        .build());
  }

  /**
   * Add a remote repository to this MavenUtils instance.
   *
   * @param remoteRepository the RemoteRepository to add
   * @return this MavenUtils instance
   */
  public MavenUtils addRemoteRepository(RemoteRepository remoteRepository) {
    remoteRepositories.add(remoteRepository);
    return this;
  }

  /**
   * Get the list of remote repositories used by this MavenUtils instance.
   *
   * @return list of RemoteRepository
   */
  public List<RemoteRepository> getRemoteRepositories() {
    return remoteRepositories;
  }
}

package se.alipsa.mavenutils;

import static se.alipsa.mavenutils.EnvUtils.getUserHome;

import org.eclipse.aether.resolution.*;
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
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import javax.annotation.Nullable;
import java.io.File;
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
  private static final RemoteRepository CENTRAL_MAVEN_REPOSITORY = getCentralMavenRepository();
  private static final RemoteRepository BE_DATA_DRIVEN_MAVEN_REPOSITORY = getBeDataDrivenMavenRepository();

  private final List<RemoteRepository> remoteRepositories;

  /**
   * Default constructor, will use Maven Central and BeDataDriven as remote repositories
   */
  public MavenUtils() {
    remoteRepositories = getDefaultRemoteRepositories();
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
    this.remoteRepositories = remoteRepositories;
  }

  public static List<RemoteRepository> getDefaultRemoteRepositories() {
    return Arrays.asList(CENTRAL_MAVEN_REPOSITORY, BE_DATA_DRIVEN_MAVEN_REPOSITORY);
  }

  public static RemoteRepository getCentralMavenRepository() {
    return new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
        .build();
  }

  public static RemoteRepository getBeDataDrivenMavenRepository() {
    return new RemoteRepository.Builder("bedatadriven", "default", "https://nexus.bedatadriven.com/content/groups/public/")
        .build();
  }

  /**
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
    File dir = pomFile.getParentFile();
    Properties sysProps = EnvUtils.parseArguments(mvnArgs);
    InvocationRequest request = new DefaultInvocationRequest()
        .setBatchMode(true)
        .setPomFile( pomFile )
        .setGoals(Arrays.asList(mvnArgs))
        .setBaseDirectory(dir);

    if (!sysProps.isEmpty()) {
      request.getProperties().putAll(sysProps);
    }

    LOG.info("Running maven from dir {} with args {}", dir, String.join(" ", mvnArgs));
    Invoker invoker = new DefaultInvoker();
    String mavenHome = locateMavenHome();
    File mavenHomeDir = new File(mavenHome);
    if (mavenHomeDir.exists()) {
      LOG.debug("MAVEN_HOME used is {}", mavenHome);
      invoker.setMavenHome(mavenHomeDir);
    } else {
      // Without maven home, only a small subset of maven commands will work
      LOG.warn("No MAVEN_HOME set or set to an non existing maven home: {}, this might not go well...", mavenHome);
    }
    invoker.setOutputHandler(consoleOutputHandler == null ? new ConsoleInvocationOutputHandler() : consoleOutputHandler);
    invoker.setErrorHandler(warningOutputHandler == null ? new WarningInvocationOutputHandler() : warningOutputHandler);
    return invoker.execute( request );
  }

  public static String locateMavenHome() {
    String mavenHome = System.getProperty("MAVEN_HOME", System.getenv("MAVEN_HOME"));
    if (mavenHome == null) {
      mavenHome = locateMaven();
    }
    return mavenHome;
  }

  public ClassLoader getMavenDependenciesClassloader(File pomFile, @Nullable ClassLoader possibleParent) throws Exception {
    return getMavenClassLoader(parsePom(pomFile), resolveDependencies(pomFile), possibleParent);
  }

  /**
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

  public Set<File> resolveDependencies(File pomFile, boolean... includeTestScope) throws SettingsBuildingException, ModelBuildingException,
      DependenciesResolveException {
    RepositorySystem repositorySystem = getRepositorySystem();
    RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);
    boolean testScope = includeTestScope.length > 0 && includeTestScope[0];
    Model model = parsePom(pomFile);
    List<RemoteRepository> repositories = getRepositories(model);
    Set<File> dependencies = new HashSet<>();
    LOG.trace("Maven model resolved: {}, parsing its dependencies...", model);
    List<String> includeScopes = new ArrayList<>();
    includeScopes.add(JavaScopes.COMPILE);
    includeScopes.add(JavaScopes.RUNTIME);
    if (testScope) {
      includeScopes.add(JavaScopes.TEST);
    }
    for (org.apache.maven.model.Dependency d : model.getDependencies()) {
      LOG.trace("processing dependency: {}", d);
      Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getType(), d.getVersion());

      if (includeScopes.contains(d.getScope())) {
        ///// Resolve main + transient
        LOG.info("resolving {}:{}:{}:{}...", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), d.getType() );
        CollectRequest collectRequest = new CollectRequest(new Dependency(artifact, JavaScopes.RUNTIME), repositories);
        DependencyFilter filter = DependencyFilterUtils.classpathFilter(includeScopes);
        DependencyRequest request = new DependencyRequest(collectRequest, filter);

        DependencyResult result;
        try {
          result = repositorySystem.resolveDependencies(repositorySystemSession, request);
        } catch (DependencyResolutionException | RuntimeException e) {
          LOG.warn("Error resolving dependent artifact: {}:{}:{}", d.getGroupId(), d.getArtifactId(), d.getVersion(), e);
          throw new DependenciesResolveException("Error resolving dependent artifact: " + d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion(), e);
        }

        for (ArtifactResult artifactResult : result.getArtifactResults()) {
          Artifact art = artifactResult.getArtifact();
          LOG.debug("artifact {} resolved to {}", art, art.getFile());
          dependencies.add(art.getFile());
        }
      }
    }
    return dependencies;
  }

  /**
   *
   * @param pomFile the pom.xml file to parse
   * @return a Model (i.e. the Maven object representation of the pom file)
   * @throws SettingsBuildingException if there was some issue with building the maven settings context
   * @throws ModelBuildingException if there was some issue with the pom file
   */
  public Model parsePom(File pomFile) throws SettingsBuildingException, ModelBuildingException {
    final DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()
       .setPomFile(pomFile);
    RepositorySystem repositorySystem = getRepositorySystem();
    RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);
    modelBuildingRequest.setModelResolver(new ModelResolver(
        // TODO: we need a model to get the real remote repositories but we dont have that yet
        //  cheating by using the ones passed in
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
    String[] pathElements = path.split(System.getProperty("path.separator"));
    for (String elem : pathElements) {
      File dir = new File(elem);
      if (dir.exists()) {
        String [] files = dir.list();
        if (files != null) {
          boolean foundMvn = Arrays.asList(files).contains("mvn");
          if (foundMvn) {
            return dir.getParentFile().getAbsolutePath();
          }
        }
      }
    }
    return "";
  }

  private static RepositorySystem getRepositorySystem() {
    DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();
    serviceLocator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    serviceLocator.addService(TransporterFactory.class, FileTransporterFactory.class);

    serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    serviceLocator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        LOG.warn("Error creating Maven service", exception);
      }
    });

    return serviceLocator.getService(RepositorySystem.class);
  }

  private static DefaultRepositorySystemSession getRepositorySystemSession(RepositorySystem system) throws SettingsBuildingException {
    DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = getLocalRepository();
    repositorySystemSession.setLocalRepositoryManager(
       system.newLocalRepositoryManager(repositorySystemSession, localRepository));

    repositorySystemSession.setRepositoryListener(new ConsoleRepositoryEventListener());

    return repositorySystemSession;
  }

  public static LocalRepository getLocalRepository() throws SettingsBuildingException {
    Settings settings = getSettings();
    String localRepoPath = settings.getLocalRepository();

    if (localRepoPath != null) {
      localRepoPath = localRepoPath.replace("${user.home}", EnvUtils.getUserHome().getAbsolutePath());
    } else {
      localRepoPath = new File(EnvUtils.getUserHome(), ".m2/repository").getAbsolutePath();
    }
    return new LocalRepository(localRepoPath);
  }

  private static Settings getSettings() throws SettingsBuildingException {
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
    String m2Home = System.getenv("M2_HOME") != null ? System.getenv("M2_HOME") : System.getenv("MAVEN_HOME");
    if (m2Home != null) {
      File globalSettingsFile = new File(m2Home, "conf/settings.xml");
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

}

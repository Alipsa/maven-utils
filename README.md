# maven-utils
High level api to interact with maven from within the jvm

Use it by adding the dependency to your maven pom:
```xml
<dependency>
    <groupId>se.alipsa</groupId>
    <artifactId>maven-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Basic usage

### Running a maven goal
```groovy
import org.apache.maven.shared.invoker.InvocationResult;
import se.alipsa.maven.MavenUtils;

File pomFile = new File("pom.xml");
InvocationResult result = MavenUtils.runMaven(pomFile, new String[]{"clean", "install"}, null, null);
```
The arguments to `runMaven(final File pomFile, String[] mvnArgs,
@Nullable InvocationOutputHandler consoleOutputHandler,
@Nullable InvocationOutputHandler warningOutputHandler)` are as follows:
- pomFile the pom.xml file to parse
- mvnArgs the arguments (targets) to send to maven (e.g. clean install)
- consoleOutputHandler where normal maven output will be sent, defaults to System.out if null
- warningOutputHandler where maven warning outputs will be sent, defaults to System.err if null
- InvocationResult the result of running the targets
- MavenInvocationException if there is a problem with parsing or running maven


For the methods below an instance of MavenUtils must be created. This allows you to pass in
a list of RemoteRepositories used for the resolution. If you use the default constructor (as in the examples below)
you get the Maven Central and BeDatadriven repositories.

### Parse a pom file into a Model

```groovy
import java.io.File;
import org.apache.maven.model.Model;
import se.alipsa.maven.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
Model model = mavenUtils.parsePom(pomFile);
```

### Get a Classloader with all dependencies resolved

```groovy
import java.io.File;
import se.alipsa.maven.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
ClassLoader loader = mavenUtils.getMavenDependenciesClassloader(pomFile, this.getClass().getClassLoader())
```
The method is defined as `getMavenDependenciesClassloader(File pomFile, @Nullable ClassLoader possibleParent)` 

### Resolve a pom file and get a Set of files for the dependencies (and their transients)

```groovy
import java.util.Set;
import java.io.File;
import se.alipsa.maven.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
```

### Fetch (resolve) a single artifact
```groovy
import se.alipsa.maven.MavenUtils;
import java.io.File;

MavenUtils mavenUtils = new MavenUtils();
File file = mavenUtils.resolveArtifact("org.slf4j", "slf4j-api", null, "jar", "1.7.32");
```

The method is defined as `resolveArtifact(String groupId, String artifactId, String classifier, String extension, String version)`
- groupId is the same as the <groupId> tag in the pom.xml
- artifactId is the same as the <artifactId> tag in the pom.xml
- classifier is typically null, javadoc, sources, dist etc
- extension could be pom, jar, zip etc.
- version is the same as the <version> tag in the pom.xml


For a more elaborate explanation see [the maven documentation](https://maven.apache.org/pom.html)
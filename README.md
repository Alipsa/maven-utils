# maven-utils

The maven-utils artifact is based on the latest maven 3 release. As such is "should" work together with older distributions of maven. However, to ensure compatibility with older maven versions, separate artifacts are published for maven 3.9.11, 3.9.4, 3.8.4 and 3.3.9. Once a new maven version is released, a new artifact will be published for that version and the maven-utils artifact will be updated to use the latest maven version. The maven-utils artifact will always use the latest maven version and should be used if you want to use the latest features of maven. The other artifacts should be used if you want to ensure compatibility with a particular older maven version.

maven-utils:
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-utils/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-utils)
[![javadoc](https://javadoc.io/badge2/se.alipsa/maven-utils/javadoc.svg)](https://javadoc.io/doc/se.alipsa/maven-utils)

maven-3.9.11-utils:
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.9.11-utils/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.9.11-utils)
[![javadoc](https://javadoc.io/badge2/se.alipsa/maven-3.9.11-utils/javadoc.svg)](https://javadoc.io/doc/se.alipsa/maven-3.9.11-utils)

maven-3.9.4-utils:
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.9.4-utils/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.9.4-utils)
[![javadoc](https://javadoc.io/badge2/se.alipsa/maven-3.9.4-utils/javadoc.svg)](https://javadoc.io/doc/se.alipsa/maven-3.9.4-utils)

maven-3.8.4-utils: 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.8.4-utils/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.8.4-utils)
[![javadoc](https://javadoc.io/badge2/se.alipsa/maven-3.8.4-utils/javadoc.svg)](https://javadoc.io/doc/se.alipsa/maven-3.8.4-utils)

maven-3.3.9-utils: 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.3.9-utils/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/maven-3.3.9-utils)
[![javadoc](https://javadoc.io/badge2/se.alipsa/maven-3.3.9-utils/javadoc.svg)](https://javadoc.io/doc/se.alipsa/maven-3.3.9-utils)

High level api to interact with maven from within the jvm

Use it by adding the dependency to your maven pom, e.g:
```xml
<dependency>
    <groupId>se.alipsa</groupId>
    <artifactId>maven-utils</artifactId>
    <version>1.3.0</version>
</dependency>
```

## Basic usage

### Running a maven goal
```groovy
import org.apache.maven.shared.invoker.InvocationResult;
import se.alipsa.mavenutils.MavenUtils;

File pomFile = new File("pom.xml");
InvocationResult result = MavenUtils.runMaven(pomFile, new String[]{"clean", "install"}, null, null);
```
The arguments to `runMaven(final File pomFile, String[] mvnArgs,
@Nullable File javaHome,
@Nullable InvocationOutputHandler consoleOutputHandler,
@Nullable InvocationOutputHandler warningOutputHandler)` are as follows:
- pomFile the pom.xml file to parse
- mvnArgs the arguments (targets) to send to maven (e.g. clean install). Flags (like `-DskipTests`, `-Pprofile`, `-pl module`) are parsed into the appropriate invocation request fields rather than being treated as goals.
- javaHome an optional Java home to use for this invocation; if null, the default JAVA_HOME is used
- consoleOutputHandler where normal maven output will be sent, defaults to System.out if null
- warningOutputHandler where maven warning outputs will be sent, defaults to System.err if null
- InvocationResult the result of running the targets
- MavenInvocationException if there is a problem with parsing or running maven

### Maven distribution selection (runMaven and resolveDependencies)

MavenUtils now supports deterministic maven distribution selection with modes:
- WRAPPER
- HOME
- DEFAULT

Default precedence is:
- WRAPPER (`mvnw` on Unix, `mvnw.cmd` on Windows, and `.mvn/wrapper/maven-wrapper.properties` present)
- configured Maven home (when provided through options)
- DEFAULT (`locateMavenHome()`)

Existing APIs remain backwards compatible but are now wrapper-preferred by default.

Use `MavenExecutionOptions` to control behavior per call:
```groovy
import se.alipsa.mavenutils.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils.MavenExecutionOptions options = new MavenUtils.MavenExecutionOptions(
    pomFile.getParentFile(),   // projectDir (optional)
    null,                      // configuredMavenHome (optional)
    true                       // preferWrapper
);
```

To get selection metadata for logging/debugging:
```groovy
import se.alipsa.mavenutils.MavenUtils;

MavenUtils.MavenRunResult runResult = MavenUtils.runMavenWithSelection(
    pomFile,
    new String[]{"validate"},
    null,
    options,
    null,
    null
);
println("Maven mode used: " + runResult.getDistributionSelection().getMode());
```

Note that maven need to be installed locally for DEFAULT mode to work. MavenUtils will first
look for the MAVEN_HOME system property, then the MAVEN_HOME environment variable and if still not found will try to locate
the mvn command in the PATH.

The static method locateMavenHome is used to find maven home.
```groovy
import se.alipsa.mavenutils.MavenUtils;

String mavenHome = MavenUtils.locateMavenHome();
```


Get the local repository
```groovy
import se.alipsa.mavenutils.MavenUtils;
import org.eclipse.aether.repository.LocalRepository;

LocalRepository localRepository = MavenUtils.getLocalRepository();
```

<hr />
For the methods below, an instance of MavenUtils must be created. This allows you to pass in
a list of RemoteRepositories used for the resolution. If you use the default constructor (as in the examples below)
you get the Maven Central repository.

### Parse a pom file into a Model

```groovy
import java.io.File;
import org.apache.maven.model.Model;
import se.alipsa.mavenutils.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
Model model = mavenUtils.parsePom(pomFile);
```

### Get a Classloader with all dependencies resolved

```groovy
import java.io.File;
import se.alipsa.mavenutils.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
ClassLoader loader = mavenUtils.getMavenDependenciesClassloader(pomFile, this.getClass().getClassLoader())
```
The method is defined as `getMavenDependenciesClassloader(File pomFile, @Nullable ClassLoader possibleParent)` 

### Resolve a pom file and get a Set of files for the dependencies (and their transients)

```groovy
import java.util.Set;
import java.io.File;
import se.alipsa.mavenutils.MavenUtils;

File pomFile = new File("pom.xml");
MavenUtils mavenUtils = new MavenUtils();
Set<File> dependencies = mavenUtils.resolveDependencies(pomFile);
```

To get dependency resolution metadata:
```groovy
MavenUtils.DependenciesResolutionResult resolveResult =
    mavenUtils.resolveDependenciesWithSelection(pomFile, options);
println("Maven mode used: " + resolveResult.getDistributionSelection().getMode());
Set<File> dependencies = resolveResult.getDependencies();
```

### Fetch (resolve) a single artifact
```groovy
import se.alipsa.mavenutils.MavenUtils;
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

There is also a simplified version of resolveArtifact requiring only groupId, artifactId and version where classifier is "null" and extension is "jar".


For a more elaborate explanation see [the maven documentation](https://maven.apache.org/pom.html)

## Logging
Maven-utils uses slf4j for logging so a slf4j implementation needs to be present for logging to work. 

## License and dependencies
The code in this repository is licenced under the MIT license. However, maven-utils depends on a number of other libraries
to be able to do its thing. Below is a list of them with their respective license.

### org.slf4j:slf4j-api
Used for logging. Licence: MIT

### org.apache.maven.shared:maven-invoker
Used to run maven. Licence: Apache 2.0

### org.apache.maven.shared:maven-shared-utils
Used to run maven and to parse the pom file. License: Apache 2.0

### org.apache.maven:maven-core
Used to run maven and to parse the pom file. License: Apache 2.0

### org.apache.maven:maven-resolver-supplier
Used to run maven and to parse the pom file. License: Apache 2.0

### org.eclipse.aether:aether-connector-basic
Used to resolve dependencies. License: EPL 1.0

### org.eclipse.aether:aether-transport-file
Used to resolve dependencies. License: EPL 1.0

### org.eclipse.aether:aether-transport-http
Used to resolve dependencies. License: EPL 1.0

### org.junit.jupiter:junit-jupiter
User for unit testing. Licence: EPL 2.0

### org.slf4j:slf4j-simple
User for unit testing. Licence: MIT

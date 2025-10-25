# Maven Utils release history

## Version history

### 1.1.1, Oct 25, 2025
- Upgrade dependencies
- Add javadocs

### 1.1.0, Apr 17, 2025
- add addRemoteRepository methods to MavenUtils
- change default remotes to only include Maven Central
- upgrade dependencies
  - guava 33.0.0-jre -> 33.4.8-jre
  - commons-io 2.15.1 -> 2.19.0
  - junit-jupiter 5.10.2 -> 5.12.2
  - slf4j  2.0.11 -> 2.0.17
  - maven-resolver-transport-file 1.9.18 -> 1.9.22
  - maven-resolver-connector-basic 1.9.18 -> 1.9.22
  - maven-resolver-transport-http  1.9.18 -> 1.9.22
- Add more javadocs

### 1.0.3, Feb 5, 2024
- upgrade guava to fix security issue with the one included in guice
- rename package, add automatic module name
- add support for pom dependencies
- add scope filter
- made some package protected methods private
- add getter for remoteRepositories

### 1.0.2, Mar 6, 2022
- add dependency on later version of httpclient since the one that comes with aether-transport-http
  has security issues.
- improve download artifact test
- add dependency on later version commons-io since the one that comes with maven-shared-utils has security issues
- Add support for system properties in (-Dkey=value) the maven arguments
- upgrade slf4j dependency version

### 1.0.1, Jan 28, 2022
- make locateMavenHome public.

### 1.0.0, Jan 27, 2022
- Initial implementation, heavily based on similar functionality in the [Ride](https://github.com/perNyfelt/ride) project.
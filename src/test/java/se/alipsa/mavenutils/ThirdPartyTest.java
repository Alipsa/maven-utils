package se.alipsa.mavenutils;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collections;

import static se.alipsa.mavenutils.MavenUtils.*;

public class ThirdPartyTest {

  /**
   * The Maven Project Object
   *
   * @parameter expression="${project}"
   * @required2.0
   * @readonly
   */
  protected MavenProject project;

  /**
   * @component
   */
  protected ArtifactResolver artifactResolver;

  protected RemoteRepositoryManager remoteRepositoryManager;

  private Object invoke( Object object, String method )
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return object.getClass().getMethod( method ).invoke( object );
  }

  private org.apache.maven.model.resolution.ModelResolver makeModelResolver() throws MojoExecutionException {
    try {
      RepositorySystem repositorySystem = getRepositorySystem();
      RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);
      return new ModelResolver(
          Collections.singletonList(getCentralMavenRepository()),
          repositorySystemSession,
          repositorySystem
      );
    } catch (Exception e) {
      throw new MojoExecutionException("Error instantiating DefaultModelResolver", e);
    }
  }

  public Model resolveEffectiveModel(File pomFile) {
    try {
      ModelBuilder modelBuilder = new ParentPomsAsDependencyModelBuilder();
      return modelBuilder.build(makeModelBuildRequest(pomFile)).getEffectiveModel();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ModelBuildingRequest makeModelBuildRequest(File artifactFile) throws MojoExecutionException {
    org.apache.maven.model.resolution.ModelResolver modelResolver = makeModelResolver();
    DefaultModelBuildingRequest mbr = new DefaultModelBuildingRequest();
    mbr.setPomFile(artifactFile);
    mbr.setModelResolver(modelResolver); // <-- the hard-to-get modelResolver
    return mbr;
  }

  @Test
  public void testDependencyResolving() throws URISyntaxException {
    File pomFile = Paths.get(getClass().getResource("/pom/transientPom.xml").toURI()).toFile();
    Model model = resolveEffectiveModel(pomFile);
    for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
      System.out.println(dependency);
    }

  }
}

package se.alipsa.mavenutils;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.*;

import java.io.File;
import java.util.List;

/**
 * This class is a custom implementation of the ModelBuilder interface that modifies the
 * behavior of the default model builder to include parent POMs as dependencies in the
 * effective model.
 */
public class ParentPomsAsDependencyModelBuilder implements ModelBuilder {

   private final DefaultModelBuilder delegate;

   /**
    * Constructs a ParentPomsAsDependencyModelBuilder that delegates to the default model builder.
    */
   public ParentPomsAsDependencyModelBuilder() {
      delegate = new DefaultModelBuilderFactory().newInstance();
   }

   @Override
   public ModelBuildingResult build(ModelBuildingRequest request) throws ModelBuildingException {
      return new ParentPomsAsDependencyModelBuildingResult(delegate.build(request));
   }

   @Override
   public ModelBuildingResult build(ModelBuildingRequest request, ModelBuildingResult result) throws ModelBuildingException {
      return delegate.build(request, result);
   }

   @Override
   public Result<? extends Model> buildRawModel(File file, int validationLevel, boolean locationTracking) {
      return delegate.buildRawModel(file, validationLevel, locationTracking);
   }

   private static class ParentPomsAsDependencyModelBuildingResult implements ModelBuildingResult {

      private final ModelBuildingResult wrapped;

      public ParentPomsAsDependencyModelBuildingResult(ModelBuildingResult wrapped) {
         this.wrapped = wrapped;
      }

      @Override
      public Model getEffectiveModel() {
         Model original = wrapped.getEffectiveModel();
         Parent parent = original.getParent();
         if (parent != null) {
            Model clone = original.clone();
            Dependency parentDependency = new Dependency();
            parentDependency.setGroupId(parent.getGroupId());
            parentDependency.setArtifactId(parent.getArtifactId());
            parentDependency.setVersion(parent.getVersion());
            parentDependency.setScope("compile");
            parentDependency.setType("pom");
            clone.addDependency(parentDependency);
            return clone;
         } else {
            return original;
         }
      }

      @Override
      public List<String> getModelIds() {
         return wrapped.getModelIds();
      }

      @Override
      public Model getRawModel() {
         return wrapped.getRawModel();
      }

      @Override
      public Model getRawModel(String modelId) {
         return wrapped.getRawModel(modelId);
      }

      @Override
      public List<Profile> getActivePomProfiles(String modelId) {
         return wrapped.getActivePomProfiles(modelId);
      }

      @Override
      public List<Profile> getActiveExternalProfiles() {
         return wrapped.getActiveExternalProfiles();
      }

      @Override
      public List<ModelProblem> getProblems() {
         return wrapped.getProblems();
      }
   }
}

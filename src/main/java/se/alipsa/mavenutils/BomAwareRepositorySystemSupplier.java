package se.alipsa.mavenutils;

import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

/**
 * A {@link RepositorySystemSupplier} subclass that provides a BOM-aware {@link ModelBuilder}.
 * <p>
 * The default {@link RepositorySystemSupplier} already uses {@link DefaultModelBuilderFactory}
 * to create the {@link ModelBuilder}, which supports BOM ({@code <scope>import</scope>})
 * resolution. This subclass makes the customization point explicit and ensures the
 * {@link ModelBuilder} used by the {@link org.apache.maven.repository.internal.DefaultArtifactDescriptorReader}
 * can resolve BOM imports in transitive dependency POMs.
 * </p>
 */
class BomAwareRepositorySystemSupplier extends RepositorySystemSupplier {

  @Override
  protected ModelBuilder getModelBuilder() {
    return new DefaultModelBuilderFactory().newInstance();
  }
}

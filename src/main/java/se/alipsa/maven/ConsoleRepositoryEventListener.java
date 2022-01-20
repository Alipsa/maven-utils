package se.alipsa.maven;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleRepositoryEventListener extends AbstractRepositoryListener {

   private static final Logger LOG = LoggerFactory.getLogger(ConsoleRepositoryEventListener.class);

   @Override
   public void artifactInstalled(RepositoryEvent event) {
      LOG.debug("artifact {} installed to file {}", event.getArtifact(), event.getFile());
   }

   @Override
   public void artifactInstalling(RepositoryEvent event) {
      LOG.debug("installing artifact {} to file {}", event.getArtifact(), event.getFile());
   }

   @Override
   public void artifactResolved(RepositoryEvent event) {
      LOG.debug("artifact {} resolved from repository {}", event.getArtifact(),
         event.getRepository());
   }

   @Override
   public void artifactDownloading(RepositoryEvent event) {
      LOG.debug("downloading artifact {} from repository {}", event.getArtifact(),
         event.getRepository());
   }

   @Override
   public void artifactDownloaded(RepositoryEvent event) {
      LOG.debug("downloaded artifact {} from repository {}", event.getArtifact(),
         event.getRepository());
   }

   @Override
   public void artifactResolving(RepositoryEvent event) {
      LOG.debug("resolving artifact {}", event.getArtifact());
   }

}

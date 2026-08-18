package ua.quietlymavenplugin.discovery;

import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.scan.EntityScanOptions;
import ua.quietlycore.scan.FilterScanner;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.util.List;

public class ProjectDiscovery
{

   private final MavenProject project;
   private final QuietlyPluginConfig config;

   public ProjectDiscovery(MavenProject project, QuietlyPluginConfig config)
   {
      this.project = project;
      this.config = config;
   }

   public DiscoveredProject discoverFilteredApplicationEntities() throws Exception
   {
      return discover(EntityScanOptions.filteredApplicationEntities(config.entityPackagePatternForScan()));
   }

   public DiscoveredProject discoverAllApplicationEntities() throws Exception
   {
      return discover(EntityScanOptions.allApplicationEntities(config.entityPackagePatternForScan()));
   }

   private DiscoveredProject discover(EntityScanOptions options) throws Exception
   {
      List<FilterEntityInfo> entities = FilterScanner.scanProjectEntities(
               project.getCompileClasspathElements(),
               config.moduleContext().outputDirectory().toString(),
               options
      );
      return new DiscoveredProject(config.moduleContext(), entities);
   }
}

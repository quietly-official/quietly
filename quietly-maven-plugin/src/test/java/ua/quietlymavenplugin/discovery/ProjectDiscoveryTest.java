package ua.quietlymavenplugin.discovery;

import org.apache.maven.model.Build;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.scan.EntityScanOptions;
import ua.quietlycore.scan.FilterScanner;
import ua.quietlymavenplugin.discovery.fixtures.DiscoveryFilteredEntity;
import ua.quietlymavenplugin.discovery.fixtures.DiscoveryPlainEntity;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDiscoveryTest
{

   @TempDir
   Path tempDir;

   @Test
   public void discovery_returns_current_module_context() throws Exception
   {
      MavenProject project = project("customer-app", "jar");
      QuietlyPluginConfig config = config(project, null);

      DiscoveredProject discoveredProject = new ProjectDiscovery(project, config).discoverFilteredApplicationEntities();

      assertEquals("customer-app", discoveredProject.moduleContext().artifactId());
      assertEquals("jar", discoveredProject.moduleContext().packaging());
      assertEquals(tempDir, discoveredProject.moduleContext().basedir());
      assertEquals(tempDir.resolve("target"), discoveredProject.moduleContext().buildDirectory());
      assertEquals(Path.of(testClassesPath()), discoveredProject.moduleContext().outputDirectory());
      assertEquals(tempDir.resolve("target/generated-test-sources/quietly"),
               discoveredProject.moduleContext().generatedTestOutputDirectory());
      assertEquals(0, discoveredProject.moduleContext().reactorModuleCount());
      assertFalse(discoveredProject.moduleContext().pomPackaging());
   }

   @Test
   public void filtered_discovery_matches_filter_scanner_results() throws Exception
   {
      MavenProject project = project("customer-app", "jar");
      QuietlyPluginConfig config = config(project, null);

      DiscoveredProject discoveredProject = new ProjectDiscovery(project, config).discoverFilteredApplicationEntities();
      List<FilterEntityInfo> directScan = FilterScanner.scanProjectEntities(
               project.getCompileClasspathElements(),
               project.getBuild().getOutputDirectory(),
               EntityScanOptions.filteredApplicationEntities(config.entityPackagePatternForScan())
      );

      assertEquals(entityClasses(directScan), entityClasses(discoveredProject.entities()));
      assertEquals(List.of("obj.status"), filterNames(discoveredProject.entities()));
   }

   @Test
   public void all_entities_discovery_includes_plain_entities_for_crud() throws Exception
   {
      MavenProject project = project("customer-app", "jar");
      QuietlyPluginConfig config = config(project, null);

      DiscoveredProject discoveredProject = new ProjectDiscovery(project, config).discoverAllApplicationEntities();

      assertTrue(entityClasses(discoveredProject.entities()).contains(DiscoveryFilteredEntity.class));
      assertTrue(entityClasses(discoveredProject.entities()).contains(DiscoveryPlainEntity.class));
   }

   @Test
   public void custom_generated_output_is_reflected_in_module_context() throws Exception
   {
      MavenProject project = project("customer-app", "jar");
      QuietlyPluginConfig config = config(project, new File("custom-generated-tests"));

      DiscoveredProject discoveredProject = new ProjectDiscovery(project, config).discoverFilteredApplicationEntities();

      assertEquals(tempDir.resolve("custom-generated-tests"),
               discoveredProject.moduleContext().generatedTestOutputDirectory());
   }

   private QuietlyPluginConfig config(MavenProject project, File testOutputDirectory)
   {
      return new QuietlyPluginConfig(
               project,
               null,
               "ua.quietlymavenplugin.discovery.fixtures",
               null,
               null,
               testOutputDirectory,
               tempDir.resolve("report.md").toFile(),
               false,
               true,
               true,
               false,
               FieldResolutionMode.STRICT
      );
   }

   private MavenProject project(String artifactId, String packaging) throws Exception
   {
      MavenProject project = new TestMavenProject(List.of(testClassesPath()));
      project.setFile(tempDir.resolve("pom.xml").toFile());
      project.setArtifactId(artifactId);
      project.setPackaging(packaging);
      Build build = new Build();
      build.setDirectory(tempDir.resolve("target").toString());
      build.setOutputDirectory(testClassesPath());
      project.setBuild(build);
      return project;
   }

   private String testClassesPath() throws Exception
   {
      return Path.of(ProjectDiscoveryTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
               .toString();
   }

   private Set<Class<?>> entityClasses(List<FilterEntityInfo> entities)
   {
      return entities.stream().map(FilterEntityInfo::entityClass).collect(Collectors.toSet());
   }

   private List<String> filterNames(List<FilterEntityInfo> entities)
   {
      return entities.stream()
               .flatMap(entity -> entity.filters().stream())
               .map(filter -> filter.prefix + "." + filter.field)
               .toList();
   }

   private static class TestMavenProject extends MavenProject
   {
      private final List<String> compileClasspathElements;

      TestMavenProject(List<String> compileClasspathElements)
      {
         this.compileClasspathElements = compileClasspathElements;
      }

      @Override
      public List<String> getCompileClasspathElements() throws DependencyResolutionRequiredException
      {
         return compileClasspathElements;
      }
   }
}

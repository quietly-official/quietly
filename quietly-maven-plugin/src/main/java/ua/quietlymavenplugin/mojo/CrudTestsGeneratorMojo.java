package ua.quietlymavenplugin.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.scan.EntityScanOptions;
import ua.quietlycore.scan.FilterScanner;
import ua.quietlymavenplugin.render.CrudTestsCodeGenerator;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.util.List;

/**
 * Generates or updates conventional JUnit CRUD smoke tests for matching REST services and registers the generated
 * directory as a Maven test source root.
 */
@Mojo(
         name = "crud-tests",
         defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CrudTestsGeneratorMojo extends AbstractMojo
{

   /**
    * Maven project being processed. Supplied by Maven and not configurable by users.
    */
   @Parameter(defaultValue = "${project}", readonly = true, required = true)
   private MavenProject project;

   /**
    * Root Java package used by Quietly conventions. When omitted, Quietly derives the legacy default from the project.
    */
   @Parameter
   private String basePackage;

   /**
    * Package pattern used to select entities. Supports {@code ${basePackage}}.
    */
   @Parameter
   private String entityPackagePattern;

   /**
    * Package pattern used to locate REST services. Supports {@code ${basePackage}}.
    */
   @Parameter
   private String servicePackagePattern;

   /**
    * REST service class-name pattern. Supports {@code ${entitySimpleName}}.
    */
   @Parameter
   private String serviceNamePattern;

   /**
    * Directory receiving generated test sources. Relative paths are resolved from the project base directory.
    * Defaults to {@code target/generated-test-sources/quietly}.
    */
   @Parameter
   private File testOutputDirectory;

   /**
    * Markdown CRUD report path. Relative paths are resolved from the project base directory.
    * Defaults to {@code target/quietly/crud-report.md}.
    */
   @Parameter
   private File reportFile;

   /**
    * Adds {@code @Disabled} to generated CRUD tests when enabled. Defaults to {@code false}.
    */
   @Parameter(defaultValue = "false")
   private boolean disabledByDefault;

   /**
    * Fails generation when an entity has no matching REST service. Defaults to {@code true}.
    */
   @Parameter(defaultValue = "true")
   private boolean failOnMissingService;

   /**
    * Reports what would be generated without writing or registering test sources. Defaults to {@code false}.
    */
   @Parameter(defaultValue = "false")
   private boolean dryRun;

   @Override
   public void execute() throws MojoExecutionException
   {
      getLog().info(Constants.QUIETLY_INFO + "Generating CRUD smoke tests for REST services");

      try
      {
         QuietlyPluginConfig config = new QuietlyPluginConfig(
                  project,
                  basePackage,
                  entityPackagePattern,
                  servicePackagePattern,
                  serviceNamePattern,
                  testOutputDirectory,
                  effectiveReportFile(),
                  disabledByDefault,
                  failOnMissingService,
                  true,
                  dryRun,
                  FieldResolutionMode.STRICT
         );
         if (!config.dryRun())
         {
            project.addTestCompileSourceRoot(config.testOutputDirectory().toString());
         }

         List<FilterEntityInfo> entities = FilterScanner.scanProjectEntities(
                  project.getCompileClasspathElements(),
                  project.getBuild().getOutputDirectory(),
                  EntityScanOptions.allApplicationEntities(config.entityPackagePatternForScan())
         );

         CrudTestsCodeGenerator generator = new CrudTestsCodeGenerator(getLog(), project, config);
         generator.generateCrudTests(entities);
      }
      catch (Exception e)
      {
         throw new MojoExecutionException("Quietly CRUD test generation failed", e);
      }
   }

   private File effectiveReportFile()
   {
      if (reportFile != null)
      {
         return reportFile;
      }
      String buildDirectory = project.getBuild() == null || project.getBuild().getDirectory() == null
               ? new File(project.getBasedir(), "target").getPath()
               : project.getBuild().getDirectory();
      return new File(buildDirectory, "quietly/crud-report.md");
   }
}

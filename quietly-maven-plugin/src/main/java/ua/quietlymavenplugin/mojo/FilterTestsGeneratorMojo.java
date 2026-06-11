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
import ua.quietlymavenplugin.render.FilterTestsCodeGenerator;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.util.List;

/**
 * Generates or updates JUnit integration tests for Hibernate filters and registers the generated directory as a Maven
 * test source root. Bind this goal to {@code generate-test-sources} so a plain {@code mvn test} uses the generated tests.
 */
@Mojo(
         name = "filter-tests",
         defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class FilterTestsGeneratorMojo extends AbstractMojo
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
    * Markdown report path. Relative paths are resolved from the project base directory.
    * Defaults to {@code target/quietly/filters-report.md}.
    */
   @Parameter
   private File reportFile;

   /**
    * Adds {@code @Disabled} to generated tests when enabled. Defaults to {@code false}.
    */
   @Parameter(defaultValue = "false")
   private boolean disabledByDefault;

   /**
    * Fails generation when an entity with filters has no matching REST service. Defaults to {@code true}.
    */
   @Parameter(defaultValue = "true")
   private boolean failOnMissingService;

   /**
    * Fails generation when a filter field cannot be resolved deterministically. Defaults to {@code true}.
    */
   @Parameter(defaultValue = "true")
   private boolean failOnUnresolvedField;

   /**
    * Reports what would be generated without writing or registering test sources. Defaults to {@code false}.
    */
   @Parameter(defaultValue = "false")
   private boolean dryRun;

   /**
    * Field matching strategy. Defaults to {@code STRICT}; {@code FUZZY} enables heuristic matching with diagnostics.
    */
   @Parameter(defaultValue = "STRICT")
   private FieldResolutionMode fieldResolutionMode;

   static void registerGeneratedTestSource(MavenProject project, QuietlyPluginConfig config)
   {
      project.addTestCompileSourceRoot(config.testOutputDirectory().toString());
   }

   @Override
   public void execute() throws MojoExecutionException
   {
      getLog().info(Constants.QUIETLY_INFO + "Generating JUnit tests for entities filters");

      try
      {
         QuietlyPluginConfig config = new QuietlyPluginConfig(
                  project,
                  basePackage,
                  entityPackagePattern,
                  servicePackagePattern,
                  serviceNamePattern,
                  testOutputDirectory,
                  reportFile,
                  disabledByDefault,
                  failOnMissingService,
                  failOnUnresolvedField,
                  dryRun,
                  fieldResolutionMode
         );
         if (!config.dryRun())
         {
            registerGeneratedTestSource(project, config);
         }

         List<FilterEntityInfo> entitiesFilters =
                  FilterScanner.scanProjectEntities(
                           project.getCompileClasspathElements(),
                           project.getBuild().getOutputDirectory(),
                           EntityScanOptions.filteredApplicationEntities(config.entityPackagePatternForScan()));

         FilterTestsCodeGenerator generator = new FilterTestsCodeGenerator(getLog(), project, config);
         generator.generateFilterTests(entitiesFilters);

      }
      catch (Exception e)
      {
         throw new MojoExecutionException("Quietly generate-tests failed, ", e);
      }
   }
}

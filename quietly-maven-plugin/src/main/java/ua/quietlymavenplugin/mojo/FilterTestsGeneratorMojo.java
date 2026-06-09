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

@Mojo(
         name = "filter-tests",
         defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class FilterTestsGeneratorMojo extends AbstractMojo {

   @Parameter(defaultValue = "${project}", readonly = true, required = true)
   private MavenProject project;

   @Parameter
   private String basePackage;

   @Parameter
   private String entityPackagePattern;

   @Parameter
   private String servicePackagePattern;

   @Parameter
   private String serviceNamePattern;

   @Parameter
   private File testOutputDirectory;

   @Parameter
   private File reportFile;

   @Parameter(defaultValue = "false")
   private boolean disabledByDefault;

   @Parameter(defaultValue = "true")
   private boolean failOnMissingService;

   @Parameter(defaultValue = "true")
   private boolean failOnUnresolvedField;

   @Parameter(defaultValue = "false")
   private boolean dryRun;

   @Parameter(defaultValue = "STRICT")
   private FieldResolutionMode fieldResolutionMode;

   @Override
   public void execute() throws MojoExecutionException {
      getLog().info(Constants.QUIETLY_INFO + "Generating JUnit tests for entities filters");

      try {
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
         if (!config.dryRun()) {
            project.addTestCompileSourceRoot(config.testOutputDirectory().toString());
         }

         List<FilterEntityInfo> entitiesFilters =
                  FilterScanner.scanProjectEntities(
                           project.getCompileClasspathElements(),
                           project.getBuild().getOutputDirectory(),
                           EntityScanOptions.filteredApplicationEntities(config.entityPackagePatternForScan()));

         FilterTestsCodeGenerator generator = new FilterTestsCodeGenerator(getLog(), project, config);
         generator.generateFilterTests(entitiesFilters);

      } catch (Exception e) {
         throw new MojoExecutionException("Quietly generate-tests failed, ", e);
      }
   }
}

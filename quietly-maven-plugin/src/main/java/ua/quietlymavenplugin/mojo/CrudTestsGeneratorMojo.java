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

@Mojo(
         name = "crud-tests",
         defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CrudTestsGeneratorMojo extends AbstractMojo
{

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

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
import ua.quietlymavenplugin.render.QuietlyProjectAnalyzer;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.report.QuietlyReport;

import java.io.File;
import java.util.List;

/**
 * Discovers Hibernate filters declared on application entities and writes an inventory report.
 * This goal does not evaluate whether tests can be generated and does not write test sources.
 */
@Mojo(
         name = "scan",
         defaultPhase = LifecyclePhase.PROCESS_CLASSES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class FilterScanMojo extends AbstractMojo
{

   /**
    * Maven project being analyzed. Supplied by Maven and not configurable by users.
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
    * Generated-test directory inspected by reports. Relative paths are resolved from the project base directory.
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
    * Field matching strategy used when reporting filter metadata. Defaults to {@code STRICT};
    * use {@code FUZZY} only when legacy naming requires heuristic matching.
    */
   @Parameter(defaultValue = "STRICT")
   private FieldResolutionMode fieldResolutionMode;

   @Override
   public void execute() throws MojoExecutionException
   {
      getLog().info(Constants.QUIETLY_INFO + "Scanning filters in project");

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
                  false,
                  false,
                  false,
                  true,
                  fieldResolutionMode
         );

         List<FilterEntityInfo> entities = FilterScanner.scanProjectEntities(
                  project.getCompileClasspathElements(),
                  project.getBuild().getOutputDirectory(),
                  EntityScanOptions.filteredApplicationEntities(config.entityPackagePatternForScan())
         );
         QuietlyProjectAnalyzer analyzer = new QuietlyProjectAnalyzer(getLog(), project, config);
         QuietlyReport report = analyzer.scan(entities);
         analyzer.logSummary(report);
         getLog().info(Constants.QUIETLY_INFO + "Wrote report: " + config.reportFile());
         getLog().info(Constants.QUIETLY_INFO + "Wrote JSON report: " + config.jsonReportFile());
      }
      catch (Exception e)
      {
         throw new MojoExecutionException("Quietly scan failed", e);
      }
   }
}

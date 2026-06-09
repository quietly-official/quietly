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

@Mojo(
         name = "scan",
         defaultPhase = LifecyclePhase.PROCESS_CLASSES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class FilterScanMojo extends AbstractMojo {

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

   @Parameter(defaultValue = "STRICT")
   private FieldResolutionMode fieldResolutionMode;

   @Override
   public void execute() throws MojoExecutionException {
      getLog().info(Constants.QUIETLY_INFO + "Scanning filters in project");

      try {
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
      } catch (Exception e) {
         throw new MojoExecutionException("Quietly scan failed", e);
      }
   }
}

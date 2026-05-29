package ua.quietlymavenplugin.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.scan.FilterScanner;
import ua.quietlymavenplugin.render.config.Constants;

@Mojo(
         name = "filters-scan",
         defaultPhase = LifecyclePhase.PROCESS_CLASSES,
         threadSafe = true,
         requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class FilterScanMojo extends AbstractMojo {

   @Parameter(defaultValue = "${project}", readonly = true, required = true)
   private MavenProject project;

   @Override
   public void execute() throws MojoExecutionException {
      getLog().info(Constants.QUIETLY_INFO + "Scanning entities in project");

      try {
         FilterScanner.scanProjectEntities(
                  project.getCompileClasspathElements(),
                  project.getBuild().getOutputDirectory()
         );
      } catch (Exception e) {
         throw new MojoExecutionException("Quietly scan failed", e);
      }
   }
}

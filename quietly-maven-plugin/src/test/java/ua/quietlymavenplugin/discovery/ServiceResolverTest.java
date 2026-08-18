package ua.quietlymavenplugin.discovery;

import com.acme.model.Customer;
import com.acme.services.rs.CustomerServiceRs;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceResolverTest
{

   @TempDir
   Path tempDir;

   @Test
   public void resolves_existing_service_with_default_conventions() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, null, null);

      try (ServiceResolver resolver = new ServiceResolver(project, config))
      {
         ServiceResolution resolution = resolver.resolve(Customer.class);

         assertEquals("com.acme.services.rs", resolution.servicePackage());
         assertEquals("CustomerServiceRs", resolution.serviceSimpleName());
         assertEquals("com.acme.services.rs.CustomerServiceRs", resolution.expectedClassName());
         assertTrue(resolution.exists());
         assertEquals(CustomerServiceRs.class, resolution.resolvedClass().orElseThrow());
         assertTrue(resolution.failureReason().isEmpty());
      }
   }

   @Test
   public void reports_missing_service_with_expected_class_name() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, null, "Missing${entitySimpleName}Service");

      try (ServiceResolver resolver = new ServiceResolver(project, config))
      {
         ServiceResolution resolution = resolver.resolve(Customer.class);

         assertEquals("com.acme.services.rs.MissingCustomerService", resolution.expectedClassName());
         assertFalse(resolution.exists());
         assertTrue(resolution.resolvedClass().isEmpty());
         assertTrue(resolution.failureReason().orElseThrow()
                  .contains("Configure servicePackagePattern/serviceNamePattern"));
      }
   }

   @Test
   public void resolves_custom_package_and_service_name() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, "com.acme.services.rs", "${entitySimpleName}ServiceRs");

      try (ServiceResolver resolver = new ServiceResolver(project, config))
      {
         ServiceResolution resolution = resolver.resolve(Customer.class);

         assertEquals("com.acme.services.rs", resolution.servicePackage());
         assertEquals("CustomerServiceRs", resolution.serviceSimpleName());
         assertTrue(resolution.exists());
      }
   }

   private QuietlyPluginConfig config(MavenProject project, String servicePackagePattern, String serviceNamePattern)
   {
      return new QuietlyPluginConfig(
               project,
               null,
               null,
               servicePackagePattern,
               serviceNamePattern,
               tempDir.resolve("generated-tests").toFile(),
               tempDir.resolve("report.md").toFile(),
               false,
               true,
               true,
               false,
               FieldResolutionMode.STRICT
      );
   }

   private MavenProject project() throws Exception
   {
      TestMavenProject project = new TestMavenProject(List.of(testClassesPath()));
      File pom = tempDir.resolve("pom.xml").toFile();
      Files.writeString(pom.toPath(), "<project />");
      project.setFile(pom);
      return project;
   }

   private String testClassesPath()
   {
      return Path.of(Customer.class.getProtectionDomain().getCodeSource().getLocation().getPath()).toString();
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

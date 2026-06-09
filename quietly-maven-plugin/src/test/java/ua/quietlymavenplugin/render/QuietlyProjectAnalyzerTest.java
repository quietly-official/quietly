package ua.quietlymavenplugin.render;

import com.acme.model.Customer;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.report.QuietlyReport;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QuietlyProjectAnalyzerTest
{

   @TempDir
   Path tempDir;

   @Test
   public void scan_is_a_pure_inventory_and_does_not_require_service_or_field_resolution() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, "Missing${entitySimpleName}Service", "scan-report.md");
      QuietlyProjectAnalyzer analyzer = new QuietlyProjectAnalyzer(new SystemStreamLog(), project, config);

      QuietlyReport report = analyzer.scan(List.of(
               new FilterEntityInfo(Customer.class, List.of(filter("obj", "field_not_present")))
      ));

      assertEquals(1, report.discoveredFilters());
      assertEquals(0, report.diagnostics());
      assertEquals(1, report.entries().size());
      assertEquals("DISCOVERED", report.entries().get(0).status());

      String markdown = Files.readString(config.reportFile());
      assertTrue(markdown.contains("# Quietly Filter Scan Report"));
      assertTrue(markdown.contains("- Discovered filters: `1`"));
      assertFalse(markdown.contains("SKIPPED_MISSING_SERVICE"));
      assertFalse(markdown.contains("SKIPPED_UNRESOLVED_FIELD"));
   }

   @Test
   public void doctor_calculates_readiness_separately_from_existing_test_coverage() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, null, "doctor-report.md");
      QuietlyProjectAnalyzer analyzer = new QuietlyProjectAnalyzer(new SystemStreamLog(), project, config);

      QuietlyReport report = analyzer.doctor(List.of(
               new FilterEntityInfo(Customer.class, List.of(
                        filter("obj", "status"),
                        filter("obj", "field_not_present")
               ))
      ));

      assertEquals(2, report.discoveredFilters());
      assertEquals(1, report.readyFilters());
      assertEquals(50.0, report.readinessPercent());
      assertEquals(0, report.generatedFilters());
      assertTrue(report.hasProblems());

      String markdown = Files.readString(config.reportFile());
      assertTrue(markdown.contains("# Quietly Project Diagnostics Report"));
      assertTrue(markdown.contains("- Analyzed filters: `2`"));
      assertTrue(markdown.contains("- Ready filters: `1`"));
      assertTrue(markdown.contains("- Readiness: `50.00%`"));
      assertTrue(markdown.contains("- Existing generated tests: `0`"));
      assertTrue(markdown.contains("SKIPPED_UNRESOLVED_FIELD"));
      assertTrue(markdown.contains("MISSING_TABLE_NAME"));
   }

   private MavenProject project() throws Exception
   {
      TestMavenProject project = new TestMavenProject(List.of(testClassesPath()));
      File pom = tempDir.resolve("pom.xml").toFile();
      Files.writeString(pom.toPath(), "<project />");
      project.setFile(pom);
      return project;
   }

   private QuietlyPluginConfig config(MavenProject project, String serviceNamePattern, String reportName)
   {
      return new QuietlyPluginConfig(
               project,
               null,
               null,
               null,
               serviceNamePattern,
               tempDir.resolve("generated-tests").toFile(),
               tempDir.resolve(reportName).toFile(),
               false,
               false,
               false,
               true,
               FieldResolutionMode.STRICT
      );
   }

   private String testClassesPath()
   {
      return Path.of(Customer.class.getProtectionDomain().getCodeSource().getLocation().getPath()).toString();
   }

   private FilterInfo filter(String prefix, String field)
   {
      FilterInfo filter = new FilterInfo();
      filter.prefix = prefix;
      filter.field = field;
      filter.paramType = String.class;
      return filter;
   }

   private static class TestMavenProject extends MavenProject
   {

      private final List<String> compileClasspathElements;

      TestMavenProject(List<String> compileClasspathElements)
      {
         this.compileClasspathElements = compileClasspathElements;
      }

      @Override
      public List<String> getCompileClasspathElements()
      {
         return compileClasspathElements;
      }
   }
}

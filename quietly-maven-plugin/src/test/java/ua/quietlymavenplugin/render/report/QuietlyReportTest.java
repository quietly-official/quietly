package ua.quietlymavenplugin.render.report;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class QuietlyReportTest
{

   @TempDir
   Path tempDir;

   @Test
   public void duplicate_events_for_same_filter_do_not_increase_total()
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);

      report.addFilter("Customer", "obj.status", "GENERATED", "first");
      report.addFilter("Customer", "obj.status", "GENERATED", "duplicate event");

      assertEquals(1, report.totalFilters());
      assertEquals(1, report.coveredFilters());
   }

   @Test
   public void ok_and_existing_for_same_filter_count_as_one_covered_filter()
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);

      report.addFilter("Customer", "obj.status", "OK", "resolved");
      report.addFilter("Customer", "obj.status", "EXISTING", "test exists");

      assertEquals(1, report.totalFilters());
      assertEquals(1, report.coveredFilters());
      assertEquals(1, report.readyFilters());
      assertEquals(1, report.generatedFilters());
      assertEquals(100.0, report.coveragePercent());
   }

   @Test
   public void discovered_filter_is_not_covered_until_generation_is_present()
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_SCAN);

      report.addFilter("Customer", "obj.status", "DISCOVERED", "found");

      assertEquals(1, report.discoveredFilters());
      assertEquals(0, report.coveredFilters());
      assertEquals(0, report.generatedFilters());
      assertEquals(0.0, report.coveragePercent());
   }

   @Test
   public void filter_blocked_by_missing_service_stays_in_the_coverage_denominator()
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);

      report.addDiagnostic("Customer", "missing-service", "SKIPPED_MISSING_SERVICE", "missing");
      report.addFilter("Customer", "obj.status", "SKIPPED_MISSING_SERVICE", "missing");
      report.addFilter("Customer", "obj.status", "SKIPPED_MISSING_SERVICE", "duplicate event");

      assertEquals(1, report.discoveredFilters());
      assertEquals(0, report.coveredFilters());
      assertEquals(0.0, report.coveragePercent());
      assertEquals(2, report.problems());
      assertTrue(report.hasProblems());
   }

   @Test
   public void ok_filter_is_semantically_covered()
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);

      report.addFilter("Customer", "obj.status", "OK", "service and field resolved");

      assertEquals(1, report.coveredFilters());
      assertEquals(1, report.readyFilters());
   }

   @Test
   public void capabilities_have_separate_summaries()
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);

      report.addFilter("Customer", "obj.status", "OK", "resolved");
      report.addCrudOperation("Customer", "list", "GENERATED", "generated");
      report.addDiagnostic("Customer", "sql-fixture", "MISSING_SQL_FIXTURE", "missing");

      assertEquals(1, report.totalFilters());
      assertEquals(1, report.coveredFilters());
      assertEquals(1, report.readyFilters());
      assertEquals(1, report.totalCrudOperations());
      assertEquals(1, report.coveredCrudOperations());
      assertEquals(1, report.diagnostics());
      assertTrue(report.hasProblems());
   }

   @Test
   public void scan_markdown_reports_inventory_without_a_misleading_coverage() throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_SCAN);
      report.addFilter("Customer", "obj.status", "DISCOVERED", "found");

      QuietlyPluginConfig config = config();
      report.write(config);

      String markdown = Files.readString(config.reportFile());
      String json = Files.readString(config.jsonReportFile());
      assertTrue(markdown.contains("- Discovered filters: `1`"));
      assertFalse(markdown.contains("coverage"));
      assertFalse(markdown.contains("Covered filters"));
      assertTrue(json.contains("\"discoveredFilters\": 1"));
      assertFalse(json.contains("coveragePercent"));
      assertFalse(json.contains("readinessPercent"));
      assertFalse(json.contains("crudCoveragePercent"));
   }

   @Test
   public void empty_reports_use_zero_instead_of_false_one_hundred_percent()
   {
      QuietlyReport filterReport = new QuietlyReport(ReportType.FILTER_GENERATION);
      QuietlyReport doctorReport = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);
      QuietlyReport crudReport = new QuietlyReport(ReportType.CRUD_GENERATION);

      assertEquals(0.0, filterReport.generationCoveragePercent());
      assertEquals(0.0, doctorReport.readinessPercent());
      assertEquals(0.0, crudReport.crudCoveragePercent());
   }

   @Test
   public void doctor_json_contains_readiness_and_generation_coverage_but_not_crud_coverage() throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);
      report.addFilter("Customer", "obj.status", "OK", "ready");
      report.addFilter("Customer", "obj.status", "EXISTING", "generated test exists");
      report.addFilter("Customer", "obj.name", "OK", "ready");

      QuietlyPluginConfig config = config();
      report.write(config);

      String markdown = Files.readString(config.reportFile());
      String json = Files.readString(config.jsonReportFile());
      assertTrue(markdown.contains("- Generation coverage: `50.00%`"));
      assertTrue(json.contains("\"analyzedFilters\": 2"));
      assertTrue(json.contains("\"readyFilters\": 2"));
      assertTrue(json.contains("\"readinessPercent\": 100.00"));
      assertTrue(json.contains("\"existingGeneratedTests\": 1"));
      assertTrue(json.contains("\"generationCoveragePercent\": 50.00"));
      assertFalse(json.contains("\"coveragePercent\""));
      assertFalse(json.contains("\"crudCoveragePercent\""));
   }

   @Test
   public void crud_report_has_crud_title_and_consistent_summary() throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.CRUD_GENERATION);
      report.addCrudOperation("Customer", "list", "GENERATED", "generated");
      report.addCrudOperation("Customer", "list", "EXISTING", "same logical operation");
      report.addCrudOperation("Customer", "get-missing", "DISCOVERED", "not generated");

      QuietlyPluginConfig config = config();
      report.write(config);

      String markdown = Files.readString(config.reportFile());
      String json = Files.readString(config.jsonReportFile());

      assertTrue(markdown.contains("# Quietly CRUD Generation Report"));
      assertTrue(markdown.contains("- CRUD operations: `2`"));
      assertTrue(markdown.contains("- Covered CRUD operations: `1`"));
      assertTrue(markdown.contains("- CRUD coverage: `50.00%`"));
      assertTrue(json.contains("\"crudOperations\": 2"));
      assertTrue(json.contains("\"coveredCrudOperations\": 1"));
      assertTrue(json.contains("\"crudCoveragePercent\": 50.00"));
   }

   private QuietlyPluginConfig config() throws Exception
   {
      MavenProject project = new MavenProject();
      File pom = tempDir.resolve("pom.xml").toFile();
      Files.writeString(pom.toPath(), "<project />");
      project.setFile(pom);

      return new QuietlyPluginConfig(
               project,
               null,
               null,
               null,
               null,
               tempDir.resolve("generated-tests").toFile(),
               tempDir.resolve("report.md").toFile(),
               false,
               true,
               true,
               false,
               FieldResolutionMode.STRICT
      );
   }
}

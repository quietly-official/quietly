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
      assertEquals(1, report.generatableFilters());
      assertEquals(1, report.generatedTestClasses());
      assertEquals(1, report.generatedTestMethods());
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
      assertEquals(1, report.generatableFilters());
      assertEquals(100.0, report.coveragePercent());
      assertEquals(100.0, report.generationReadinessPercent());
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
      assertEquals(0, report.generatableFilters());
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
      assertEquals(0, report.generatableFilters());
      assertEquals(1, report.blockedFilters());
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
      assertTrue(markdown.contains("- Generatable filters: `not evaluated by scan`"));
      assertTrue(markdown.contains("- Generation readiness: `not evaluated by scan`"));
      assertTrue(markdown.contains("- Execution: `NOT_MEASURED`"));
      assertTrue(json.contains("\"discoveredFilters\": 1"));
      assertTrue(json.contains("\"generatableFilters\": null"));
      assertTrue(json.contains("\"generationReadinessPercent\": null"));
      assertTrue(json.contains("\"status\": \"NOT_MEASURED\""));
      assertTrue(json.contains("\"measuredByQuietly\": false"));
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
      assertEquals(0.0, filterReport.generationReadinessPercent());
      assertEquals(0.0, doctorReport.readinessPercent());
      assertEquals(0.0, crudReport.crudCoveragePercent());
   }

   @Test
   public void doctor_json_separates_readiness_generation_and_execution_while_retaining_legacy_fields()
            throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);
      report.addFilter("Customer", "obj.status", "OK", "ready");
      report.addFilter("Customer", "obj.status", "EXISTING", "generated test exists");
      report.addFilter("Customer", "obj.name", "OK", "ready");

      QuietlyPluginConfig config = config();
      report.write(config);

      String markdown = Files.readString(config.reportFile());
      String json = Files.readString(config.jsonReportFile());
      assertTrue(markdown.contains("- Discovered filters: `2`"));
      assertTrue(markdown.contains("- Generatable filters: `2`"));
      assertTrue(markdown.contains("- Blocked/ambiguous filters: `0`"));
      assertTrue(markdown.contains("- Generation readiness: `100.00%`"));
      assertTrue(markdown.contains("- Generated test classes: `1`"));
      assertTrue(markdown.contains("- Generated test methods: `1`"));
      assertTrue(markdown.contains("- Execution: `NOT_MEASURED`"));
      assertTrue(json.contains("\"analyzedFilters\": 2"));
      assertTrue(json.contains("\"readyFilters\": 2"));
      assertTrue(json.contains("\"readinessPercent\": 100.00"));
      assertTrue(json.contains("\"existingGeneratedTests\": 1"));
      assertTrue(json.contains("\"discoveredFilters\": 2"));
      assertTrue(json.contains("\"generatableFilters\": 2"));
      assertTrue(json.contains("\"blockedFilters\": 0"));
      assertTrue(json.contains("\"generationReadinessPercent\": 100.00"));
      assertTrue(json.contains("\"generatedTestClasses\": 1"));
      assertTrue(json.contains("\"generatedTestMethods\": 1"));
      // Legacy field retained for existing JSON consumers.
      assertTrue(json.contains("\"generationCoveragePercent\": 50.00"));
      assertTrue(json.contains("\"status\": \"NOT_MEASURED\""));
      assertFalse(json.contains("\"coveragePercent\""));
      assertFalse(json.contains("\"crudCoveragePercent\""));
   }

   @Test
   public void generation_report_counts_distinct_classes_methods_and_blocked_filters() throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);
      report.addFilter("Customer", "obj.status", "GENERATED", "generated");
      report.addFilter("Customer", "obj.name", "EXISTING", "already generated");
      report.addFilter("Order", "obj.status", "SKIPPED_UNRESOLVED_FIELD", "ambiguous");

      QuietlyPluginConfig config = config();
      report.write(config);

      String markdown = Files.readString(config.reportFile());
      String json = Files.readString(config.jsonReportFile());

      assertEquals(3, report.discoveredFilters());
      assertEquals(2, report.generatableFilters());
      assertEquals(1, report.blockedFilters());
      assertEquals(1, report.generatedTestClasses());
      assertEquals(2, report.generatedTestMethods());
      assertEquals(66.66666666666667, report.generationReadinessPercent());

      assertTrue(markdown.contains("- Discovered filters: `3`"));
      assertTrue(markdown.contains("- Generatable filters: `2`"));
      assertTrue(markdown.contains("- Blocked/ambiguous filters: `1`"));
      assertTrue(markdown.contains("- Generation readiness: `66.67%`"));
      assertTrue(markdown.contains("- Generated test classes: `1`"));
      assertTrue(markdown.contains("- Generated test methods: `2`"));
      assertTrue(markdown.contains("does not measure whether generated tests passed"));

      assertTrue(json.contains("\"discoveredFilters\": 3"));
      assertTrue(json.contains("\"generatableFilters\": 2"));
      assertTrue(json.contains("\"blockedFilters\": 1"));
      assertTrue(json.contains("\"generationReadinessPercent\": 66.67"));
      assertTrue(json.contains("\"generatedTestClasses\": 1"));
      assertTrue(json.contains("\"generatedTestMethods\": 2"));
      assertTrue(json.contains("\"measuredByQuietly\": false"));
      // Legacy generation fields remain available for existing consumers.
      assertTrue(json.contains("\"coveragePercent\": 66.67"));
      assertTrue(json.contains("\"generationCoveragePercent\": 66.67"));
      assertTrue(json.contains("\"filterCoveragePercent\": 66.67"));
   }

   @Test
   public void dry_run_is_generatable_without_claiming_generated_artifacts()
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);
      report.addFilter("Customer", "obj.status", "WOULD_GENERATE", "dry run");

      assertEquals(1, report.discoveredFilters());
      assertEquals(1, report.generatableFilters());
      assertEquals(100.0, report.generationReadinessPercent());
      assertEquals(0, report.generatedTestClasses());
      assertEquals(0, report.generatedTestMethods());
   }

   @Test
   public void stale_generated_test_is_not_counted_as_a_blocked_discovered_filter()
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);
      report.addFilter("Customer", "obj.removed", "STALE_GENERATED_TEST", "removed filter");

      assertEquals(0, report.discoveredFilters());
      assertEquals(0, report.blockedFilters());
      assertEquals(1, report.problems());
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

package ua.quietlymavenplugin.plan;

import com.acme.model.Customer;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.discovery.DiscoveredProject;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DoctorPlannerTest
{

   @TempDir
   Path tempDir;

   @Test
   public void missing_service_blocks_filter_test() throws Exception
   {
      MavenProject project = project();
      GenerationPlan plan = plan(
               project,
               config(project, "Missing${entitySimpleName}Service", "report.md"),
               List.of(new FilterEntityInfo(Customer.class, List.of(filter("obj", "status"))))
      );

      PlanEntry filterEntry = entry(plan, "obj.status", false);
      assertEquals(GenerationCapability.FILTER_TEST, filterEntry.capability());
      assertEquals(PlanState.BLOCKED, filterEntry.state());
      assertEquals("SKIPPED_MISSING_SERVICE", filterEntry.reportStatus());
      assertTrue(filterEntry.serviceResolution().orElseThrow().failureReason().orElseThrow()
               .contains("Configure servicePackagePattern/serviceNamePattern"));
   }

   @Test
   public void unresolved_field_blocks_filter_test() throws Exception
   {
      MavenProject project = project();
      GenerationPlan plan = plan(
               project,
               config(project, null, "report.md"),
               List.of(new FilterEntityInfo(Customer.class, List.of(filter("obj", "missing"))))
      );

      PlanEntry filterEntry = entry(plan, "obj.missing", false);
      assertEquals(PlanState.BLOCKED, filterEntry.state());
      assertEquals("SKIPPED_UNRESOLVED_FIELD", filterEntry.reportStatus());
      assertFalse(filterEntry.fieldResolution().orElseThrow().resolved());
   }

   @Test
   public void missing_fixture_blocks_fixture_diagnostic() throws Exception
   {
      MavenProject project = project();
      GenerationPlan plan = plan(
               project,
               config(project, null, "report.md"),
               List.of(new FilterEntityInfo(EntityWithTableName.class, List.of()))
      );

      PlanEntry fixtureEntry = entry(plan, "sql-fixture", true);
      assertEquals(PlanState.BLOCKED, fixtureEntry.state());
      assertEquals("MISSING_SQL_FIXTURE", fixtureEntry.reportStatus());
      assertFalse(fixtureEntry.fixtureResolution().orElseThrow().exists());
   }

   @Test
   public void existing_generated_test_is_existing() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, null, "report.md");
      Path generatedFile = config.testOutputDirectory().resolve("com/acme/CustomerFiltersTest.java");
      Files.createDirectories(generatedFile.getParent());
      Files.writeString(generatedFile, """
               package com.acme;

               public class CustomerFiltersTest {
                   /**
                    * @quietly-generated filter="obj.status"
                    */
                   public void obj_status_filter_test() {
                   }
               }
               """);

      GenerationPlan plan = plan(
               project,
               config,
               List.of(new FilterEntityInfo(Customer.class, List.of(filter("obj", "status"))))
      );

      PlanEntry existingEntry = entry(plan, "obj.status", true);
      assertEquals(PlanState.EXISTING, existingEntry.state());
      assertEquals("EXISTING", existingEntry.reportStatus());
   }

   @Test
   public void stale_generated_test_is_stale() throws Exception
   {
      MavenProject project = project();
      QuietlyPluginConfig config = config(project, null, "report.md");
      Path generatedFile = config.testOutputDirectory().resolve("com/acme/CustomerFiltersTest.java");
      Files.createDirectories(generatedFile.getParent());
      Files.writeString(generatedFile, """
               package com.acme;

               public class CustomerFiltersTest {
                   /**
                    * @quietly-generated filter="obj.oldStatus"
                    */
                   public void obj_oldStatus_filter_test() {
                   }
               }
               """);

      GenerationPlan plan = plan(
               project,
               config,
               List.of(new FilterEntityInfo(Customer.class, List.of(filter("obj", "status"))))
      );

      PlanEntry staleEntry = entry(plan, "obj.oldStatus", true);
      assertEquals(PlanState.STALE, staleEntry.state());
      assertEquals("STALE_GENERATED_TEST", staleEntry.reportStatus());
   }

   @Test
   public void resolved_service_and_field_make_filter_test_ready() throws Exception
   {
      MavenProject project = project();
      GenerationPlan plan = plan(
               project,
               config(project, null, "report.md"),
               List.of(new FilterEntityInfo(Customer.class, List.of(filter("obj", "status"))))
      );

      PlanEntry filterEntry = entry(plan, "obj.status", false);
      assertEquals(PlanState.READY, filterEntry.state());
      assertEquals("OK", filterEntry.reportStatus());
      assertTrue(filterEntry.serviceResolution().orElseThrow().exists());
      assertTrue(filterEntry.fieldResolution().orElseThrow().resolved());
   }

   private GenerationPlan plan(MavenProject project, QuietlyPluginConfig config, List<FilterEntityInfo> entities)
            throws Exception
   {
      return new DoctorPlanner(project, config).plan(new DiscoveredProject(config.moduleContext(), entities));
   }

   private PlanEntry entry(GenerationPlan plan, String subject, boolean diagnostic)
   {
      return plan.entries().stream()
               .filter(entry -> entry.subject().equals(subject))
               .filter(entry -> entry.diagnostic() == diagnostic)
               .findFirst()
               .orElseThrow();
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
               true,
               true,
               false,
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

   public static class EntityWithTableName
   {
      public static final String TABLE_NAME = "customer";
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

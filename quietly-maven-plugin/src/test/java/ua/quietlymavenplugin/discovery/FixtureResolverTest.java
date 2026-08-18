package ua.quietlymavenplugin.discovery;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FixtureResolverTest
{

   @TempDir
   Path tempDir;

   @Test
   public void resolves_existing_sql_fixture_from_table_name() throws Exception
   {
      Path fixture = tempDir.resolve("src/test/resources/sql/customer.sql");
      Files.createDirectories(fixture.getParent());
      Files.writeString(fixture, "insert into customer(id) values (1);");

      FixtureResolution resolution = new FixtureResolver(config()).resolve(EntityWithTableName.class);

      assertEquals("customer", resolution.tableName().orElseThrow());
      assertEquals(fixture, resolution.expectedSqlPath().orElseThrow());
      assertTrue(resolution.exists());
      assertTrue(resolution.failureReason().isEmpty());
   }

   @Test
   public void reports_missing_sql_fixture() throws Exception
   {
      Path fixture = tempDir.resolve("src/test/resources/sql/customer.sql");

      FixtureResolution resolution = new FixtureResolver(config()).resolve(EntityWithTableName.class);

      assertEquals("customer", resolution.tableName().orElseThrow());
      assertEquals(fixture, resolution.expectedSqlPath().orElseThrow());
      assertFalse(resolution.exists());
      assertTrue(resolution.failureReason().isEmpty());
   }

   @Test
   public void reports_missing_table_name()
   {
      FixtureResolution resolution = new FixtureResolver(config()).resolve(EntityWithoutTableName.class);

      assertTrue(resolution.tableName().isEmpty());
      assertTrue(resolution.expectedSqlPath().isEmpty());
      assertFalse(resolution.exists());
      assertEquals("Entity does not expose public TABLE_NAME.", resolution.failureReason().orElseThrow());
   }

   private QuietlyPluginConfig config()
   {
      MavenProject project = new MavenProject();
      project.setFile(tempDir.resolve("pom.xml").toFile());
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

   public static class EntityWithTableName
   {
      public static final String TABLE_NAME = "customer";
   }

   public static class EntityWithoutTableName
   {
   }
}

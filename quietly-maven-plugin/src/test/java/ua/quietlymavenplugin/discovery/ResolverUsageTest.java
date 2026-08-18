package ua.quietlymavenplugin.discovery;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResolverUsageTest
{

   @Test
   public void planner_and_crud_generator_use_shared_service_resolver() throws Exception
   {
      for (Path sourceFile : serviceConsumerFiles())
      {
         String source = Files.readString(sourceFile);
         assertTrue(source.contains("ServiceResolver"), sourceFile + " should use ServiceResolver");
         assertFalse(source.contains("serviceExists("), sourceFile + " should not keep local serviceExists logic");
         assertFalse(source.contains("ProjectClassLoaderFactory"), sourceFile + " should not build service classloaders directly");
      }
   }

   @Test
   public void filter_tests_generator_uses_plan_instead_of_direct_resolvers() throws Exception
   {
      Path generator = Path.of("src/main/java/ua/quietlymavenplugin/render/FilterTestsCodeGenerator.java");
      String source = Files.readString(generator);

      assertTrue(source.contains("DoctorPlanner"));
      assertFalse(source.contains("ServiceResolver"));
      assertFalse(source.contains("FieldResolver"));
      assertFalse(source.contains("FixtureResolver"));
      assertFalse(source.contains("resolveField("));
   }

   @Test
   public void doctor_planner_uses_shared_fixture_resolver() throws Exception
   {
      Path planner = Path.of("src/main/java/ua/quietlymavenplugin/plan/DoctorPlanner.java");
      String source = Files.readString(planner);

      assertTrue(source.contains("FixtureResolver"));
      assertFalse(source.contains("getField(\"TABLE_NAME\")"));
      assertFalse(source.contains("src/test/resources/sql/"));
   }

   private List<Path> serviceConsumerFiles()
   {
      Path renderDir = Path.of("src/main/java/ua/quietlymavenplugin/render");
      Path planDir = Path.of("src/main/java/ua/quietlymavenplugin/plan");
      return List.of(
               planDir.resolve("DoctorPlanner.java"),
               renderDir.resolve("CrudTestsCodeGenerator.java")
      );
   }
}

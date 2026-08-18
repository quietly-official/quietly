package ua.quietlymavenplugin.discovery;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectDiscoveryUsageTest
{

   @Test
   public void goals_use_project_discovery_instead_of_calling_filter_scanner_directly() throws Exception
   {
      for (Path mojo : mojoFiles())
      {
         String source = Files.readString(mojo);
         assertTrue(source.contains("ProjectDiscovery"), mojo + " should use ProjectDiscovery");
         assertFalse(source.contains("FilterScanner.scanProjectEntities"),
                  mojo + " should not call FilterScanner directly");
      }
   }

   private List<Path> mojoFiles()
   {
      Path mojoDir = Path.of("src/main/java/ua/quietlymavenplugin/mojo");
      return List.of(
               mojoDir.resolve("FilterScanMojo.java"),
               mojoDir.resolve("FilterDoctorMojo.java"),
               mojoDir.resolve("FilterTestsGeneratorMojo.java"),
               mojoDir.resolve("CrudTestsGeneratorMojo.java")
      );
   }
}

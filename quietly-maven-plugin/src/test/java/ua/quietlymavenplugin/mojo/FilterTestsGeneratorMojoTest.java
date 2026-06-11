package ua.quietlymavenplugin.mojo;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterTestsGeneratorMojoTest
{

   @TempDir
   Path tempDir;

   @Test
   void registers_generated_directory_as_test_compile_source_root()
   {
      MavenProject project = new MavenProject();
      project.setFile(tempDir.resolve("pom.xml").toFile());
      Build build = new Build();
      build.setDirectory(tempDir.resolve("target").toString());
      project.setBuild(build);

      QuietlyPluginConfig config = QuietlyPluginConfig.defaults(project);

      FilterTestsGeneratorMojo.registerGeneratedTestSource(project, config);

      String expected = tempDir.resolve("target/generated-test-sources/quietly").toString();
      assertTrue(project.getTestCompileSourceRoots().contains(expected));
      assertEquals(expected, config.testOutputDirectory().toString());
   }

   @Test
   void resolves_relative_custom_output_against_project_directory()
   {
      MavenProject project = new MavenProject();
      project.setFile(tempDir.resolve("pom.xml").toFile());

      QuietlyPluginConfig config = new QuietlyPluginConfig(
               project,
               null,
               null,
               null,
               null,
               new File("custom-generated-tests"),
               null,
               false,
               true,
               true,
               false,
               null
      );

      FilterTestsGeneratorMojo.registerGeneratedTestSource(project, config);

      String expected = tempDir.resolve("custom-generated-tests").toString();
      assertTrue(project.getTestCompileSourceRoots().contains(expected));
   }
}

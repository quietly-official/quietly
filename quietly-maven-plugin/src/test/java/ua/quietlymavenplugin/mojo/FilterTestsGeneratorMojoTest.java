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
      project.setArtifactId("app");
      project.setPackaging("jar");
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
   void registers_generated_directory_on_current_module_only()
   {
      MavenProject root = new MavenProject();
      root.setFile(tempDir.resolve("pom.xml").toFile());
      root.setArtifactId("root");
      root.setPackaging("pom");

      Path moduleDir = tempDir.resolve("app");
      MavenProject app = new MavenProject();
      app.setFile(moduleDir.resolve("pom.xml").toFile());
      app.setArtifactId("app");
      app.setPackaging("jar");
      Build build = new Build();
      build.setDirectory(moduleDir.resolve("target").toString());
      app.setBuild(build);

      QuietlyPluginConfig config = QuietlyPluginConfig.defaults(app);

      FilterTestsGeneratorMojo.registerGeneratedTestSource(app, config);

      assertTrue(app.getTestCompileSourceRoots()
               .contains(moduleDir.resolve("target/generated-test-sources/quietly").toString()));
      assertTrue(root.getTestCompileSourceRoots().isEmpty());
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

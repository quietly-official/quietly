package ua.quietlymavenplugin.render.config;

import org.apache.maven.project.MavenProject;

import java.nio.file.Path;

public record ModuleContext(
         String artifactId,
         String packaging,
         Path basedir,
         Path buildDirectory,
         Path outputDirectory,
         Path generatedTestOutputDirectory,
         int reactorModuleCount
)
{

   public static ModuleContext from(MavenProject project, Path generatedTestOutputDirectory)
   {
      Path basedir = project.getBasedir().toPath().toAbsolutePath().normalize();
      Path buildDirectory = resolveBuildDirectory(project, basedir);
      return new ModuleContext(
               defaultString(project.getArtifactId()),
               defaultString(project.getPackaging()),
               basedir,
               buildDirectory,
               resolveOutputDirectory(project, buildDirectory),
               generatedTestOutputDirectory,
               project.getCollectedProjects() == null ? 0 : project.getCollectedProjects().size()
      );
   }

   public boolean pomPackaging()
   {
      return "pom".equals(packaging);
   }

   private static Path resolveBuildDirectory(MavenProject project, Path basedir)
   {
      String configuredBuildDirectory = project.getBuild() == null ? null : project.getBuild().getDirectory();
      Path buildPath = configuredBuildDirectory == null || configuredBuildDirectory.isBlank()
               ? Path.of("target")
               : Path.of(configuredBuildDirectory);
      return buildPath.isAbsolute() ? buildPath.normalize() : basedir.resolve(buildPath).toAbsolutePath().normalize();
   }

   private static Path resolveOutputDirectory(MavenProject project, Path buildDirectory)
   {
      String configuredOutputDirectory = project.getBuild() == null ? null : project.getBuild().getOutputDirectory();
      if (configuredOutputDirectory == null || configuredOutputDirectory.isBlank())
      {
         return buildDirectory.resolve("classes").toAbsolutePath().normalize();
      }

      Path outputPath = Path.of(configuredOutputDirectory);
      return outputPath.isAbsolute()
               ? outputPath.normalize()
               : project.getBasedir().toPath().resolve(outputPath).toAbsolutePath().normalize();
   }

   private static String defaultString(String value)
   {
      return value == null || value.isBlank() ? "(unknown)" : value;
   }
}

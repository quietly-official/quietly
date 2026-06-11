package ua.quietlymavenplugin.render.config;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

public class QuietlyPluginConfig
{

   private final MavenProject project;
   private final String basePackage;
   private final String entityPackagePattern;
   private final String servicePackagePattern;
   private final String serviceNamePattern;
   private final File testOutputDirectory;
   private final File reportFile;
   private final boolean disabledByDefault;
   private final boolean failOnMissingService;
   private final boolean failOnUnresolvedField;
   private final boolean dryRun;
   private final FieldResolutionMode fieldResolutionMode;

   public QuietlyPluginConfig(
            MavenProject project,
            String basePackage,
            String entityPackagePattern,
            String servicePackagePattern,
            String serviceNamePattern,
            File testOutputDirectory,
            File reportFile,
            boolean disabledByDefault,
            boolean failOnMissingService,
            boolean failOnUnresolvedField,
            boolean dryRun,
            FieldResolutionMode fieldResolutionMode
   )
   {
      this.project = project;
      this.basePackage = blankToNull(basePackage);
      this.entityPackagePattern = blankToNull(entityPackagePattern);
      this.servicePackagePattern = blankToNull(servicePackagePattern);
      this.serviceNamePattern = blankToNull(serviceNamePattern);
      this.testOutputDirectory = testOutputDirectory;
      this.reportFile = reportFile;
      this.disabledByDefault = disabledByDefault;
      this.failOnMissingService = failOnMissingService;
      this.failOnUnresolvedField = failOnUnresolvedField;
      this.dryRun = dryRun;
      this.fieldResolutionMode = fieldResolutionMode == null ? FieldResolutionMode.STRICT : fieldResolutionMode;
   }

   public static QuietlyPluginConfig defaults(MavenProject project)
   {
      return new QuietlyPluginConfig(
               project,
               null,
               null,
               null,
               null,
               null,
               null,
               false,
               true,
               true,
               false,
               FieldResolutionMode.STRICT
      );
   }

   private static String blankToNull(String value)
   {
      return value == null || value.isBlank() ? null : value;
   }

   public Path testOutputDirectory()
   {
      if (testOutputDirectory != null)
      {
         return resolveAgainstProject(testOutputDirectory.toPath());
      }
      return buildDirectory().resolve("generated-test-sources/quietly");
   }

   public boolean disabledByDefault()
   {
      return disabledByDefault;
   }

   public Path reportFile()
   {
      if (reportFile != null)
      {
         return resolveAgainstProject(reportFile.toPath());
      }
      return buildDirectory().resolve("quietly/filters-report.md");
   }

   public Path jsonReportFile()
   {
      Path markdownReport = reportFile();
      String fileName = markdownReport.getFileName().toString();
      if (fileName.endsWith(".md"))
      {
         return markdownReport.resolveSibling(fileName.substring(0, fileName.length() - 3) + ".json");
      }
      return markdownReport.resolveSibling(fileName + ".json");
   }

   public boolean failOnMissingService()
   {
      return failOnMissingService;
   }

   public boolean failOnUnresolvedField()
   {
      return failOnUnresolvedField;
   }

   public boolean dryRun()
   {
      return dryRun;
   }

   public FieldResolutionMode fieldResolutionMode()
   {
      return fieldResolutionMode;
   }

   public String entityPackagePatternForScan()
   {
      if (entityPackagePattern == null)
      {
         return null;
      }
      if (!entityPackagePattern.contains("${basePackage}"))
      {
         return entityPackagePattern;
      }
      if (basePackage == null)
      {
         throw new IllegalArgumentException(
                  "entityPackagePattern uses ${basePackage}, but basePackage is not configured."
         );
      }
      return entityPackagePattern.replace("${basePackage}", basePackage);
   }

   public String resolveRootPackage(Class<?> entityClass)
   {
      String entityPackage = entityClass.getPackageName();
      if (entityPackagePattern != null)
      {
         String configuredEntityPackage = applyPattern(entityPackagePattern, entityClass);
         if (entityPackage.equals(configuredEntityPackage) && basePackage != null)
         {
            return basePackage;
         }
      }

      return entityPackage.endsWith(".model")
               ? entityPackage.substring(0, entityPackage.lastIndexOf(".model"))
               : entityPackage;
   }

   public String resolveServicePackage(Class<?> entityClass)
   {
      if (servicePackagePattern != null)
      {
         return applyPattern(servicePackagePattern, entityClass);
      }

      return entityClass.getPackageName().endsWith(".model")
               ? entityClass.getPackageName().replace(".model", ".services.rs")
               : entityClass.getPackageName() + ".services.rs";
   }

   public String resolveServiceName(Class<?> entityClass)
   {
      if (serviceNamePattern != null)
      {
         return applyPattern(serviceNamePattern, entityClass);
      }
      return entityClass.getSimpleName() + "ServiceRs";
   }

   public String resolveServiceClassName(Class<?> entityClass)
   {
      return resolveServicePackage(entityClass) + "." + resolveServiceName(entityClass);
   }

   private String applyPattern(String pattern, Class<?> entityClass)
   {
      String effectiveBasePackage = basePackage != null ? basePackage : resolveLegacyBasePackage(entityClass);
      Map<String, String> placeholders = Map.of(
               "${basePackage}", effectiveBasePackage,
               "${entitySimpleName}", entityClass.getSimpleName()
      );

      String result = pattern;
      for (Map.Entry<String, String> entry : placeholders.entrySet())
      {
         result = result.replace(entry.getKey(), entry.getValue());
      }
      return result;
   }

   private String resolveLegacyBasePackage(Class<?> entityClass)
   {
      String entityPackage = entityClass.getPackageName();
      return entityPackage.endsWith(".model")
               ? entityPackage.substring(0, entityPackage.lastIndexOf(".model"))
               : entityPackage;
   }

   private Path buildDirectory()
   {
      String configuredBuildDirectory = project.getBuild() == null ? null : project.getBuild().getDirectory();
      Path buildPath = configuredBuildDirectory == null || configuredBuildDirectory.isBlank()
               ? Path.of("target")
               : Path.of(configuredBuildDirectory);
      return resolveAgainstProject(buildPath);
   }

   private Path resolveAgainstProject(Path path)
   {
      if (path.isAbsolute())
      {
         return path.normalize();
      }
      return project.getBasedir().toPath().resolve(path).toAbsolutePath().normalize();
   }
}

package ua.quietlymavenplugin.render;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.adapters.ProjectClassLoaderFactory;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.javaparser.FieldResolutionResult;
import ua.quietlymavenplugin.render.javaparser.FieldResolver;
import ua.quietlymavenplugin.render.report.QuietlyReport;
import ua.quietlymavenplugin.render.report.ReportType;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class QuietlyProjectAnalyzer
{

   private final Log log;
   private final MavenProject project;
   private final QuietlyPluginConfig config;

   public QuietlyProjectAnalyzer(Log log, MavenProject project, QuietlyPluginConfig config)
   {
      this.log = log;
      this.project = project;
      this.config = config;
   }

   public QuietlyReport scan(List<FilterEntityInfo> entities) throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.FILTER_SCAN);
      for (FilterEntityInfo entityInfo : entities)
      {
         for (FilterInfo filter : entityInfo.filters())
         {
            report.addFilter(
                     entityInfo.entityClass().getSimpleName(),
                     filterName(filter),
                     "DISCOVERED",
                     "Filter metadata discovered."
            );
         }
      }
      report.write(config);
      return report;
   }

   public QuietlyReport doctor(List<FilterEntityInfo> entities) throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);
      for (FilterEntityInfo entityInfo : entities)
      {
         diagnoseEntity(entityInfo, report);
      }
      report.write(config);
      return report;
   }

   private void diagnoseEntity(FilterEntityInfo entityInfo, QuietlyReport report) throws Exception
   {
      ClassLoader projectCl = ProjectClassLoaderFactory.buildProjectClassLoader(project);
      try
      {
         Class<?> entityClass = Class.forName(entityInfo.entityClass().getName(), true, projectCl);
         String entityName = entityClass.getSimpleName();
         String serviceClassName = config.resolveServiceClassName(entityClass);

         boolean serviceExists = serviceExists(serviceClassName, projectCl);
         if (!serviceExists)
         {
            report.addDiagnostic(entityName, "missing-service", "SKIPPED_MISSING_SERVICE",
                     "Expected " + serviceClassName + ". Configure servicePackagePattern/serviceNamePattern.");
         }

         for (FilterInfo filter : entityInfo.filters())
         {
            diagnoseFilter(entityClass, filter, report, serviceExists);
         }

         reportExistingGeneratedTests(entityClass, entityInfo.filters(), report);
         checkSqlFixture(entityClass, report);
      }
      finally
      {
         closeClassLoader(projectCl);
      }
   }

   private void diagnoseFilter(
            Class<?> entityClass,
            FilterInfo filter,
            QuietlyReport report,
            boolean serviceExists
   )
   {
      String filterName = filterName(filter);
      FieldResolutionResult fieldResult = FieldResolver.resolveField(entityClass, filter.field,
               config.fieldResolutionMode());

      if (!fieldResult.resolved())
      {
         report.addFilter(entityClass.getSimpleName(), filterName, "SKIPPED_UNRESOLVED_FIELD",
                  String.join("; ", fieldResult.errors()));
         return;
      }

      if (!serviceExists)
      {
         report.addFilter(entityClass.getSimpleName(), filterName, "SKIPPED_MISSING_SERVICE",
                  "No matching REST service was found for this filter.");
         return;
      }

      report.addFilter(entityClass.getSimpleName(), filterName, "OK",
               "Service and field resolved.");
   }

   private void reportExistingGeneratedTests(Class<?> entityClass, List<FilterInfo> currentFilters,
            QuietlyReport report)
   {
      Path testFile = testFilePath(entityClass);
      if (!Files.exists(testFile))
      {
         return;
      }

      try
      {
         Optional<ClassOrInterfaceDeclaration> maybeClass = StaticJavaParser
                  .parse(Files.readString(testFile, StandardCharsets.UTF_8))
                  .getClassByName(entityClass.getSimpleName() + "FiltersTest");
         if (maybeClass.isEmpty())
         {
            report.addDiagnostic(entityClass.getSimpleName(), "invalid-existing-filter-test",
                     "SKIPPED_INVALID_EXISTING_FILE",
                     "Existing file does not contain " + entityClass.getSimpleName() + "FiltersTest.");
            return;
         }

         Set<String> currentFilterNames = new HashSet<>();
         for (FilterInfo filter : currentFilters)
         {
            currentFilterNames.add(filterName(filter));
         }

         for (MethodDeclaration method : maybeClass.get().getMethods())
         {
            Optional<String> generatedFilter = extractQuietlyGeneratedFilter(method);
            if (generatedFilter.isEmpty())
            {
               continue;
            }
            if (currentFilterNames.contains(generatedFilter.get()))
            {
               report.addFilter(entityClass.getSimpleName(), generatedFilter.get(), "EXISTING",
                        "Generated test method " + method.getNameAsString() + " already exists.");
            }
            else
            {
               report.addFilter(entityClass.getSimpleName(), generatedFilter.get(), "STALE_GENERATED_TEST",
                        "Generated method " + method.getNameAsString()
                                 + " references a filter that was not discovered anymore.");
            }
         }
      }
      catch (Exception e)
      {
         report.addDiagnostic(entityClass.getSimpleName(), "invalid-existing-filter-test",
                  "SKIPPED_INVALID_EXISTING_FILE",
                  "Could not parse existing test file " + testFile + ": " + e.getMessage());
      }
   }

   private void checkSqlFixture(Class<?> entityClass, QuietlyReport report)
   {
      try
      {
         Object tableName = entityClass.getField("TABLE_NAME").get(null);
         Path sqlFixture = new File(project.getBasedir(), "src/test/resources/sql/" + tableName + ".sql").toPath();
         if (Files.exists(sqlFixture))
         {
            report.addDiagnostic(entityClass.getSimpleName(), "sql-fixture", "OK_SQL_FIXTURE",
                     "Found " + sqlFixture);
         }
         else
         {
            report.addDiagnostic(entityClass.getSimpleName(), "sql-fixture", "MISSING_SQL_FIXTURE",
                     "Expected " + sqlFixture);
         }
      }
      catch (NoSuchFieldException e)
      {
         report.addDiagnostic(entityClass.getSimpleName(), "table-name", "MISSING_TABLE_NAME",
                  "Entity does not expose public TABLE_NAME.");
      }
      catch (Exception e)
      {
         report.addDiagnostic(entityClass.getSimpleName(), "sql-fixture", "ERROR_SQL_FIXTURE",
                  "Could not inspect SQL fixture: " + e.getMessage());
      }
   }

   private Path testFilePath(Class<?> entityClass)
   {
      String rootPkg = config.resolveRootPackage(entityClass);
      return config.testOutputDirectory()
               .resolve(rootPkg.replace('.', File.separatorChar))
               .resolve(entityClass.getSimpleName() + "FiltersTest.java");
   }

   private boolean serviceExists(String serviceClassName, ClassLoader projectCl)
   {
      try
      {
         Class.forName(serviceClassName, false, projectCl);
         return true;
      }
      catch (ClassNotFoundException e)
      {
         return false;
      }
   }

   private void closeClassLoader(ClassLoader classLoader) throws Exception
   {
      if (classLoader instanceof URLClassLoader urlClassLoader)
      {
         urlClassLoader.close();
      }
   }

   private Optional<String> extractQuietlyGeneratedFilter(MethodDeclaration method)
   {
      return method.getJavadocComment()
               .map(JavadocComment::getContent)
               .flatMap(this::extractQuietlyGeneratedFilter);
   }

   private Optional<String> extractQuietlyGeneratedFilter(String javadoc)
   {
      String marker = "@quietly-generated filter=\"";
      int start = javadoc.indexOf(marker);
      if (start < 0)
      {
         return Optional.empty();
      }
      int valueStart = start + marker.length();
      int valueEnd = javadoc.indexOf('"', valueStart);
      if (valueEnd < 0)
      {
         return Optional.empty();
      }
      return Optional.of(javadoc.substring(valueStart, valueEnd));
   }

   private String filterName(FilterInfo filter)
   {
      return filter.prefix + "." + filter.field;
   }

   public void logSummary(QuietlyReport report)
   {
      log.info(Constants.QUIETLY_INFO + "Discovered filters: " + report.discoveredFilters());
      if (report.type() == ReportType.PROJECT_DIAGNOSTICS)
      {
         log.info(Constants.QUIETLY_INFO + "Ready filters: " + report.readyFilters());
         log.info(Constants.QUIETLY_INFO + "Readiness: "
                  + String.format(java.util.Locale.ROOT, "%.2f", report.readinessPercent()) + "%");
         log.info(Constants.QUIETLY_INFO + "Generation coverage: "
                  + String.format(java.util.Locale.ROOT, "%.2f", report.generationCoveragePercent()) + "%");
         log.info(Constants.QUIETLY_INFO + "Problems: " + report.problems());
      }
   }
}

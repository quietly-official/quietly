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

import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class QuietlyProjectAnalyzer {

   private final Log log;
   private final MavenProject project;
   private final QuietlyPluginConfig config;

   public QuietlyProjectAnalyzer(Log log, MavenProject project, QuietlyPluginConfig config) {
      this.log = log;
      this.project = project;
      this.config = config;
   }

   public QuietlyReport scan(List<FilterEntityInfo> entities) throws Exception {
      QuietlyReport report = new QuietlyReport();
      for (FilterEntityInfo entityInfo : entities) {
         analyzeEntity(entityInfo, report, false);
      }
      report.write(config);
      return report;
   }

   public QuietlyReport doctor(List<FilterEntityInfo> entities) throws Exception {
      QuietlyReport report = new QuietlyReport();
      for (FilterEntityInfo entityInfo : entities) {
         analyzeEntity(entityInfo, report, true);
      }
      report.write(config);
      return report;
   }

   private void analyzeEntity(FilterEntityInfo entityInfo, QuietlyReport report, boolean doctorMode) throws Exception {
      ClassLoader projectCl = ProjectClassLoaderFactory.buildProjectClassLoader(project);
      try {
         Class<?> entityClass = Class.forName(entityInfo.entityClass().getName(), true, projectCl);
         String entityName = entityClass.getSimpleName();
         String serviceClassName = config.resolveServiceClassName(entityClass);

         boolean serviceExists = serviceExists(serviceClassName, projectCl);
         if (!serviceExists) {
            report.add(entityName, "*", "SKIPPED_MISSING_SERVICE",
                     "Expected " + serviceClassName + ". Configure servicePackagePattern/serviceNamePattern.");
         }

         for (FilterInfo filter : entityInfo.filters()) {
            analyzeFilter(entityClass, filter, report, doctorMode, serviceExists);
         }

         reportExistingGeneratedTests(entityClass, entityInfo.filters(), report);
         if (doctorMode) {
            checkSqlFixture(entityClass, report);
         }
      } finally {
         closeClassLoader(projectCl);
      }
   }

   private void analyzeFilter(
            Class<?> entityClass,
            FilterInfo filter,
            QuietlyReport report,
            boolean doctorMode,
            boolean serviceExists
   ) {
      String filterName = filterName(filter);
      FieldResolutionResult fieldResult = FieldResolver.resolveField(entityClass, filter.field, config.fieldResolutionMode());

      if (!fieldResult.resolved()) {
         report.add(entityClass.getSimpleName(), filterName, "SKIPPED_UNRESOLVED_FIELD",
                  String.join("; ", fieldResult.errors()));
         return;
      }

      if (!serviceExists) {
         return;
      }

      if (doctorMode) {
         report.add(entityClass.getSimpleName(), filterName, "OK",
                  "Service and field resolved.");
      } else {
         report.add(entityClass.getSimpleName(), filterName, "DISCOVERED",
                  "Filter metadata discovered.");
      }
   }

   private void reportExistingGeneratedTests(Class<?> entityClass, List<FilterInfo> currentFilters, QuietlyReport report) {
      Path testFile = testFilePath(entityClass);
      if (!Files.exists(testFile)) {
         return;
      }

      try {
         Optional<ClassOrInterfaceDeclaration> maybeClass = StaticJavaParser
                  .parse(Files.readString(testFile, StandardCharsets.UTF_8))
                  .getClassByName(entityClass.getSimpleName() + "FiltersTest");
         if (maybeClass.isEmpty()) {
            report.add(entityClass.getSimpleName(), "*", "SKIPPED_INVALID_EXISTING_FILE",
                     "Existing file does not contain " + entityClass.getSimpleName() + "FiltersTest.");
            return;
         }

         Set<String> currentFilterNames = new HashSet<>();
         for (FilterInfo filter : currentFilters) {
            currentFilterNames.add(filterName(filter));
         }

         for (MethodDeclaration method : maybeClass.get().getMethods()) {
            Optional<String> generatedFilter = extractQuietlyGeneratedFilter(method);
            if (generatedFilter.isEmpty()) {
               continue;
            }
            if (currentFilterNames.contains(generatedFilter.get())) {
               report.add(entityClass.getSimpleName(), generatedFilter.get(), "EXISTING",
                        "Generated test method " + method.getNameAsString() + " already exists.");
            } else {
               report.add(entityClass.getSimpleName(), generatedFilter.get(), "STALE_GENERATED_TEST",
                        "Generated method " + method.getNameAsString() + " references a filter that was not discovered anymore.");
            }
         }
      } catch (Exception e) {
         report.add(entityClass.getSimpleName(), "*", "SKIPPED_INVALID_EXISTING_FILE",
                  "Could not parse existing test file " + testFile + ": " + e.getMessage());
      }
   }

   private void checkSqlFixture(Class<?> entityClass, QuietlyReport report) {
      try {
         Object tableName = entityClass.getField("TABLE_NAME").get(null);
         Path sqlFixture = new File(project.getBasedir(), "src/test/resources/sql/" + tableName + ".sql").toPath();
         if (Files.exists(sqlFixture)) {
            report.add(entityClass.getSimpleName(), "*", "OK_SQL_FIXTURE",
                     "Found " + sqlFixture);
         } else {
            report.add(entityClass.getSimpleName(), "*", "MISSING_SQL_FIXTURE",
                     "Expected " + sqlFixture);
         }
      } catch (NoSuchFieldException e) {
         report.add(entityClass.getSimpleName(), "*", "MISSING_TABLE_NAME",
                  "Entity does not expose public TABLE_NAME.");
      } catch (Exception e) {
         report.add(entityClass.getSimpleName(), "*", "ERROR_SQL_FIXTURE",
                  "Could not inspect SQL fixture: " + e.getMessage());
      }
   }

   private Path testFilePath(Class<?> entityClass) {
      String rootPkg = config.resolveRootPackage(entityClass);
      return config.testOutputDirectory()
               .resolve(rootPkg.replace('.', File.separatorChar))
               .resolve(entityClass.getSimpleName() + "FiltersTest.java");
   }

   private boolean serviceExists(String serviceClassName, ClassLoader projectCl) {
      try {
         Class.forName(serviceClassName, false, projectCl);
         return true;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }

   private void closeClassLoader(ClassLoader classLoader) throws Exception {
      if (classLoader instanceof URLClassLoader urlClassLoader) {
         urlClassLoader.close();
      }
   }

   private Optional<String> extractQuietlyGeneratedFilter(MethodDeclaration method) {
      return method.getJavadocComment()
               .map(JavadocComment::getContent)
               .flatMap(this::extractQuietlyGeneratedFilter);
   }

   private Optional<String> extractQuietlyGeneratedFilter(String javadoc) {
      String marker = "@quietly-generated filter=\"";
      int start = javadoc.indexOf(marker);
      if (start < 0) {
         return Optional.empty();
      }
      int valueStart = start + marker.length();
      int valueEnd = javadoc.indexOf('"', valueStart);
      if (valueEnd < 0) {
         return Optional.empty();
      }
      return Optional.of(javadoc.substring(valueStart, valueEnd));
   }

   private String filterName(FilterInfo filter) {
      return filter.prefix + "." + filter.field;
   }

   public void logSummary(QuietlyReport report) {
      log.info(Constants.QUIETLY_INFO + "Total filters: " + report.totalFilters());
      log.info(Constants.QUIETLY_INFO + "Covered filters: " + report.coveredFilters());
      log.info(Constants.QUIETLY_INFO + "Coverage: " + String.format("%.2f", report.coveragePercent()) + "%");
   }
}

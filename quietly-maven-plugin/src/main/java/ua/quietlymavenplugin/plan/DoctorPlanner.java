package ua.quietlymavenplugin.plan;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.discovery.DiscoveredProject;
import ua.quietlymavenplugin.discovery.FixtureResolution;
import ua.quietlymavenplugin.discovery.FixtureResolver;
import ua.quietlymavenplugin.discovery.ServiceResolution;
import ua.quietlymavenplugin.discovery.ServiceResolver;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.javaparser.FieldResolutionResult;
import ua.quietlymavenplugin.render.javaparser.FieldResolver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DoctorPlanner
{

   private final MavenProject project;
   private final QuietlyPluginConfig config;

   public DoctorPlanner(MavenProject project, QuietlyPluginConfig config)
   {
      this.project = project;
      this.config = config;
   }

   public GenerationPlan plan(DiscoveredProject discoveredProject) throws Exception
   {
      List<PlanEntry> entries = new ArrayList<>();
      try (ServiceResolver serviceResolver = new ServiceResolver(project, config))
      {
         for (FilterEntityInfo entityInfo : discoveredProject.entities())
         {
            planEntity(entityInfo, serviceResolver, entries);
         }
      }
      return new GenerationPlan(entries);
   }

   private void planEntity(
            FilterEntityInfo entityInfo,
            ServiceResolver serviceResolver,
            List<PlanEntry> entries
   ) throws Exception
   {
      Class<?> entityClass = serviceResolver.loadProjectClass(entityInfo.entityClass());
      ServiceResolution service = serviceResolver.resolve(entityClass);

      if (!service.exists())
      {
         entries.add(PlanEntry.diagnostic(
                  entityClass,
                  "missing-service",
                  PlanState.BLOCKED,
                  "SKIPPED_MISSING_SERVICE",
                  service.failureReason().orElse("Expected " + service.expectedClassName()
                           + ". Configure servicePackagePattern/serviceNamePattern."),
                  service,
                  null
         ));
      }

      for (FilterInfo filter : entityInfo.filters())
      {
         entries.add(planFilter(entityClass, filter, service));
      }

      entries.addAll(planExistingGeneratedTests(entityClass, entityInfo.filters()));
      entries.add(planSqlFixture(entityClass));
   }

   private PlanEntry planFilter(Class<?> entityClass, FilterInfo filter, ServiceResolution service)
   {
      FieldResolutionResult fieldResult = FieldResolver.resolveField(entityClass, filter.field,
               config.fieldResolutionMode());

      if (!fieldResult.resolved())
      {
         return PlanEntry.filter(
                  entityClass,
                  filter,
                  PlanState.BLOCKED,
                  "SKIPPED_UNRESOLVED_FIELD",
                  String.join("; ", fieldResult.errors()),
                  service,
                  fieldResult
         );
      }

      if (!service.exists())
      {
         return PlanEntry.filter(
                  entityClass,
                  filter,
                  PlanState.BLOCKED,
                  "SKIPPED_MISSING_SERVICE",
                  "No matching REST service was found for this filter.",
                  service,
                  fieldResult
         );
      }

      return PlanEntry.filter(
               entityClass,
               filter,
               PlanState.READY,
               "OK",
               "Service and field resolved.",
               service,
               fieldResult
      );
   }

   private List<PlanEntry> planExistingGeneratedTests(Class<?> entityClass, List<FilterInfo> currentFilters)
   {
      Path testFile = testFilePath(entityClass);
      if (!Files.exists(testFile))
      {
         return List.of();
      }

      List<PlanEntry> entries = new ArrayList<>();
      try
      {
         Optional<ClassOrInterfaceDeclaration> maybeClass = StaticJavaParser
                  .parse(Files.readString(testFile, StandardCharsets.UTF_8))
                  .getClassByName(entityClass.getSimpleName() + "FiltersTest");
         if (maybeClass.isEmpty())
         {
            entries.add(PlanEntry.diagnostic(
                     entityClass,
                     "invalid-existing-filter-test",
                     PlanState.BLOCKED,
                     "SKIPPED_INVALID_EXISTING_FILE",
                     "Existing file does not contain " + entityClass.getSimpleName() + "FiltersTest.",
                     null,
                     null
            ));
            return entries;
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
               entries.add(PlanEntry.diagnostic(
                        entityClass,
                        generatedFilter.get(),
                        PlanState.EXISTING,
                        "EXISTING",
                        "Generated test method " + method.getNameAsString() + " already exists.",
                        null,
                        null
               ));
            }
            else
            {
               entries.add(PlanEntry.diagnostic(
                        entityClass,
                        generatedFilter.get(),
                        PlanState.STALE,
                        "STALE_GENERATED_TEST",
                        "Generated method " + method.getNameAsString()
                                 + " references a filter that was not discovered anymore.",
                        null,
                        null
               ));
            }
         }
      }
      catch (Exception e)
      {
         entries.add(PlanEntry.diagnostic(
                  entityClass,
                  "invalid-existing-filter-test",
                  PlanState.BLOCKED,
                  "SKIPPED_INVALID_EXISTING_FILE",
                  "Could not parse existing test file " + testFile + ": " + e.getMessage(),
                  null,
                  null
         ));
      }
      return entries;
   }

   private PlanEntry planSqlFixture(Class<?> entityClass)
   {
      FixtureResolution fixture = new FixtureResolver(config).resolve(entityClass);
      if (fixture.tableName().isEmpty())
      {
         String message = fixture.failureReason().orElse("Entity does not expose public TABLE_NAME.");
         String status = message.startsWith("Could not inspect") ? "ERROR_SQL_FIXTURE" : "MISSING_TABLE_NAME";
         String subject = "ERROR_SQL_FIXTURE".equals(status) ? "sql-fixture" : "table-name";
         return PlanEntry.diagnostic(entityClass, subject, PlanState.BLOCKED, status, message, null, fixture);
      }

      Path sqlFixture = fixture.expectedSqlPath().orElseThrow();
      if (fixture.exists())
      {
         return PlanEntry.diagnostic(
                  entityClass,
                  "sql-fixture",
                  PlanState.READY,
                  "OK_SQL_FIXTURE",
                  "Found " + sqlFixture,
                  null,
                  fixture
         );
      }
      return PlanEntry.diagnostic(
               entityClass,
               "sql-fixture",
               PlanState.BLOCKED,
               "MISSING_SQL_FIXTURE",
               "Expected " + sqlFixture,
               null,
               fixture
      );
   }

   private Path testFilePath(Class<?> entityClass)
   {
      String rootPkg = config.resolveRootPackage(entityClass);
      return config.testOutputDirectory()
               .resolve(rootPkg.replace('.', File.separatorChar))
               .resolve(entityClass.getSimpleName() + "FiltersTest.java");
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
}

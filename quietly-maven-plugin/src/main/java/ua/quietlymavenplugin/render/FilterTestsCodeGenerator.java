package ua.quietlymavenplugin.render;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.discovery.DiscoveredProject;
import ua.quietlymavenplugin.discovery.ServiceResolution;
import ua.quietlymavenplugin.plan.DoctorPlanner;
import ua.quietlymavenplugin.plan.GenerationPlan;
import ua.quietlymavenplugin.plan.PlanEntry;
import ua.quietlymavenplugin.plan.PlanState;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.config.TestImportsConstants;
import ua.quietlymavenplugin.render.javaparser.FieldResolutionResult;
import ua.quietlymavenplugin.render.javaparser.ImportManager;
import ua.quietlymavenplugin.render.report.QuietlyReport;
import ua.quietlymavenplugin.render.report.ReportType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FilterTestsCodeGenerator
{

   private final Log log;
   private final MavenProject project;
   private final QuietlyPluginConfig config;
   private final QuietlyReport report = new QuietlyReport(ReportType.FILTER_GENERATION);

   public FilterTestsCodeGenerator(Log log, MavenProject project)
   {
      this(log, project, QuietlyPluginConfig.defaults(project));
   }

   public FilterTestsCodeGenerator(Log log, MavenProject project, QuietlyPluginConfig config)
   {
      this.log = log;
      this.project = project;
      this.config = config;
   }

   public void generateFilterTests(List<FilterEntityInfo> entities) throws Exception
   {
      Path testRoot = config.testOutputDirectory();
      GenerationPlan plan = new DoctorPlanner(project, config)
               .plan(new DiscoveredProject(config.moduleContext(), entities));

      try
      {
         for (FilterEntityInfo entityInfo : entities)
         {
            generateEntityTests(entityInfo, testRoot, plan);
         }
      }
      finally
      {
         writeReport();
      }
   }

   private void generateEntityTests(FilterEntityInfo entityInfo, Path testRoot, GenerationPlan plan) throws Exception
   {
      List<PlanEntry> entityEntries = entityEntries(plan, entityInfo.entityClass());
      if (entityEntries.isEmpty())
      {
         return;
      }

      Class<?> entityClass = entityEntries.get(0).entity();
      String entityName = entityClass.getSimpleName();
      String rootPkg = config.resolveRootPackage(entityClass);
      List<PlanEntry> filterEntries = filterEntries(entityEntries);

      Optional<ServiceResolution> missingService = filterEntries.stream()
               .map(PlanEntry::serviceResolution)
               .flatMap(Optional::stream)
               .filter(service -> !service.exists())
               .findFirst();
      if (missingService.isPresent())
      {
         String message = missingServiceMessage(entityName, missingService.orElseThrow());
         report.addDiagnostic(entityName, "missing-service", "SKIPPED_MISSING_SERVICE", message);
         for (PlanEntry filterEntry : filterEntries)
         {
            report.addFilter(entityName, filterEntry.subject(), "SKIPPED_MISSING_SERVICE", message);
         }
         if (config.failOnMissingService())
         {
            throw new QuietlyGenerationException(message);
         }
         log.warn(Constants.QUIETLY_WARN + message);
         return;
      }

      ServiceResolution service = filterEntries.stream()
               .map(PlanEntry::serviceResolution)
               .flatMap(Optional::stream)
               .findFirst()
               .orElseThrow();

      Path targetDir = testRoot.resolve(rootPkg.replace('.', File.separatorChar));
      Path testFilePath = targetDir.resolve(entityName + "FiltersTest.java");

      if (!Files.exists(testFilePath))
      {
         CompilationUnit cu = createCompilationUnit(entityClass, rootPkg, service.expectedClassName());
         ClassOrInterfaceDeclaration classDecl = cu.getClassByName(entityName + "FiltersTest").orElseThrow();

         for (PlanEntry filterEntry : filterEntries)
         {
            addFilterTestMethod(classDecl, filterEntry);
         }

         writeCompilationUnit(testFilePath, cu);
         log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would create test file: " : "Created test file: ")
                  + testFilePath.getFileName());
         return;
      }

      Optional<PlanEntry> invalidExistingFile = entityEntries.stream()
               .filter(PlanEntry::diagnostic)
               .filter(entry -> "SKIPPED_INVALID_EXISTING_FILE".equals(entry.reportStatus()))
               .findFirst();
      if (invalidExistingFile.isPresent())
      {
         PlanEntry entry = invalidExistingFile.orElseThrow();
         report.addDiagnostic(entityName, entry.subject(), entry.reportStatus(), entry.reason());
         log.warn(Constants.QUIETLY_WARN + entry.reason());
         return;
      }

      CompilationUnit cu = StaticJavaParser.parse(Files.readString(testFilePath, StandardCharsets.UTF_8));
      if (config.disabledByDefault())
      {
         ImportManager.add_imports(List.of("org.junit.jupiter.api.Disabled"), cu);
      }

      ClassOrInterfaceDeclaration classDecl = cu.getClassByName(entityName + "FiltersTest").orElseThrow();
      Set<String> existingMethodNames = new HashSet<>();
      for (MethodDeclaration method : classDecl.getMethods())
      {
         existingMethodNames.add(method.getNameAsString());
      }

      if (!existingMethodNames.contains("beforeEach"))
      {
         classDecl.addMember(FilterTestAstBuilder.buildBeforeEachMethod(entityClass));
         log.info(Constants.QUIETLY_INFO + "Added method beforeEach for: " + entityName);
      }

      for (PlanEntry filterEntry : filterEntries)
      {
         FilterInfo filter = filterEntry.filter().orElseThrow();
         String methodName = toJavaIdentifier(filter.prefix + "_" + filter.field + "_filter_test");
         if (existingMethodNames.contains(methodName))
         {
            MethodDeclaration existingMethod = classDecl.getMethodsByName(methodName).get(0);
            if (ensureQuietlyMarker(existingMethod, filter))
            {
               report.addFilter(entityName, filterEntry.subject(), "UPDATED_MARKER",
                        "Method " + methodName + " already exists; added Quietly marker.");
            }
            else
            {
               report.addFilter(entityName, filterEntry.subject(), "EXISTING",
                        "Method " + methodName + " already exists.");
            }
            continue;
         }

         if (addFilterTestMethod(classDecl, filterEntry))
         {
            log.info(Constants.QUIETLY_INFO + "Added test: " + methodName);
         }
      }

      reportStaleGeneratedTests(entityEntries, entityName);

      writeCompilationUnit(testFilePath, cu);
      log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would update test file: " : "Updated test file: ")
               + testFilePath.getFileName());
   }

   private List<PlanEntry> entityEntries(GenerationPlan plan, Class<?> entityClass)
   {
      return plan.entries().stream()
               .filter(entry -> entry.entity().getName().equals(entityClass.getName()))
               .toList();
   }

   private List<PlanEntry> filterEntries(List<PlanEntry> entityEntries)
   {
      return entityEntries.stream()
               .filter(entry -> !entry.diagnostic())
               .toList();
   }

   private String missingServiceMessage(String entityName, ServiceResolution service)
   {
      return "Entity " + entityName + " has Hibernate filters but no matching REST service was found. "
               + "Expected " + service.expectedClassName() + ". Configure servicePackagePattern/serviceNamePattern "
               + "or set failOnMissingService=false.";
   }

   private CompilationUnit createCompilationUnit(Class<?> entityClass, String rootPkg, String serviceClassName)
   {
      String entityName = entityClass.getSimpleName();
      CompilationUnit cu = new CompilationUnit();
      cu.setPackageDeclaration(rootPkg);

      ImportManager imports = new ImportManager(cu);
      imports.add_imports(TestImportsConstants.CORE_TEST_IMPORTS);
      if (config.disabledByDefault())
      {
         imports.add_import("org.junit.jupiter.api.Disabled");
      }
      imports.add_import(entityClass.getName());
      imports.add_import(serviceClassName);

      ClassOrInterfaceDeclaration classDecl = cu.addClass(entityName + "FiltersTest")
               .addExtendedType("FilterTestBase")
               .setPublic(true);

      classDecl.addAnnotation(new MarkerAnnotationExpr("QuarkusTest"));

      NormalAnnotationExpr testEndpointAnnotation = new NormalAnnotationExpr();
      testEndpointAnnotation.setName("TestHTTPEndpoint");
      ClassOrInterfaceType serviceType = StaticJavaParser.parseClassOrInterfaceType(
               config.resolveServiceName(entityClass));
      testEndpointAnnotation.addPair("value", new ClassExpr(serviceType));
      classDecl.addAnnotation(testEndpointAnnotation);

      classDecl.addMember(FilterTestAstBuilder.buildEntityManagerField());
      classDecl.addMember(FilterTestAstBuilder.buildBeforeEachMethod(entityClass));

      return cu;
   }

   private boolean addFilterTestMethod(
            ClassOrInterfaceDeclaration classDecl,
            PlanEntry filterEntry
   )
   {
      FilterInfo filter = filterEntry.filter().orElseThrow();
      Class<?> entityClass = filterEntry.entity();
      FieldResolutionResult fieldResult = filterEntry.fieldResolution().orElseThrow();
      fieldResult.warnings().forEach(log::warn);

      if (filterEntry.state() == PlanState.BLOCKED)
      {
         if (config.failOnUnresolvedField())
         {
            throw new QuietlyGenerationException(unresolvedFieldMessage(filter, entityClass, fieldResult));
         }
         String message = unresolvedFieldMessage(filter, entityClass, fieldResult)
                  + " Skipping this generated test because failOnUnresolvedField=false.";
         report.addFilter(entityClass.getSimpleName(), filterName(filter), "SKIPPED_UNRESOLVED_FIELD", message);
         log.warn(Constants.QUIETLY_WARN + message);
         return false;
      }

      classDecl.addMember(FilterTestAstBuilder.buildFilterTestMethod(
               filter,
               entityClass,
               fieldResult,
               config.disabledByDefault()
      ));
      String status = config.dryRun() ? "WOULD_GENERATE" : "GENERATED";
      String details = config.dryRun() ? "Would generate test method." : "Generated test method.";
      report.addFilter(entityClass.getSimpleName(), filterName(filter), status, details);
      return true;
   }

   private String unresolvedFieldMessage(FilterInfo filter, Class<?> entityClass, FieldResolutionResult fieldResult)
   {
      return "Filter " + filterName(filter) + " references field " + filter.field
               + ", but no deterministic field match was found on entity " + entityClass.getSimpleName()
               + ". Use fieldResolutionMode=FUZZY or fix the filter metadata. Details: "
               + String.join("; ", fieldResult.errors());
   }

   private void writeCompilationUnit(Path path, CompilationUnit cu) throws IOException
   {
      if (config.dryRun())
      {
         return;
      }

      Files.createDirectories(path.getParent());

      PrettyPrinterConfiguration conf = new PrettyPrinterConfiguration();
      conf.setIndentType(Indentation.IndentType.SPACES);
      conf.setIndentSize(4);
      conf.setPrintComments(true);
      Files.writeString(path, cu.toString(conf), StandardCharsets.UTF_8);
   }

   private void writeReport() throws IOException
   {
      report.write(config);
      log.info(Constants.QUIETLY_INFO + "Wrote report: " + config.reportFile());
      log.info(Constants.QUIETLY_INFO + "Wrote JSON report: " + config.jsonReportFile());
   }

   private String filterName(FilterInfo filter)
   {
      return filter.prefix + "." + filter.field;
   }

   private boolean ensureQuietlyMarker(MethodDeclaration method, FilterInfo filter)
   {
      String marker = quietlyMarker(filter);
      if (method.getJavadocComment()
               .map(JavadocComment::getContent)
               .map(content -> content.contains(marker))
               .orElse(false))
      {
         return false;
      }

      method.setJavadocComment(marker);
      return true;
   }

   private String quietlyMarker(FilterInfo filter)
   {
      return "@quietly-generated filter=\"" + filterName(filter) + "\"";
   }

   private void reportStaleGeneratedTests(List<PlanEntry> entityEntries, String entityName)
   {
      entityEntries.stream()
               .filter(PlanEntry::diagnostic)
               .filter(entry -> entry.state() == PlanState.STALE)
               .forEach(entry -> report.addFilter(entityName, entry.subject(), entry.reportStatus(), entry.reason()));
   }

   private String toJavaIdentifier(String value)
   {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < value.length(); i++)
      {
         char c = value.charAt(i);
         if (i == 0)
         {
            result.append(Character.isJavaIdentifierStart(c) ? c : '_');
         }
         else
         {
            result.append(Character.isJavaIdentifierPart(c) ? c : '_');
         }
      }
      return result.toString();
   }
}

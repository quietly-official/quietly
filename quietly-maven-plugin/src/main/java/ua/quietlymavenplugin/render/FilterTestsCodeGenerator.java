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
import ua.quietlymavenplugin.adapters.ProjectClassLoaderFactory;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.config.TestImportsConstants;
import ua.quietlymavenplugin.render.javaparser.ImportManager;
import ua.quietlymavenplugin.render.report.QuietlyReport;
import ua.quietlymavenplugin.render.report.ReportType;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
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

      try
      {
         for (FilterEntityInfo entityInfo : entities)
         {
            generateEntityTests(entityInfo, testRoot);
         }
      }
      finally
      {
         writeReport();
      }
   }

   private void generateEntityTests(FilterEntityInfo entityInfo, Path testRoot) throws Exception
   {
      ClassLoader projectCl = ProjectClassLoaderFactory.buildProjectClassLoader(project);
      try
      {
         Class<?> entityClass = Class.forName(entityInfo.entityClass().getName(), true, projectCl);
         String entityName = entityClass.getSimpleName();
         String rootPkg = config.resolveRootPackage(entityClass);
         String serviceClassName = config.resolveServiceClassName(entityClass);

         if (!serviceExists(serviceClassName, projectCl))
         {
            String message = "Entity " + entityName + " has Hibernate filters but no matching REST service was found. "
                     + "Expected " + serviceClassName + ". Configure servicePackagePattern/serviceNamePattern "
                     + "or set failOnMissingService=false.";
            report.addDiagnostic(entityName, "missing-service", "SKIPPED_MISSING_SERVICE", message);
            for (FilterInfo filter : entityInfo.filters())
            {
               report.addFilter(entityName, filterName(filter), "SKIPPED_MISSING_SERVICE", message);
            }
            if (config.failOnMissingService())
            {
               throw new QuietlyGenerationException(message);
            }
            log.warn(Constants.QUIETLY_WARN + message);
            return;
         }

         Path targetDir = testRoot.resolve(rootPkg.replace('.', File.separatorChar));
         Path testFilePath = targetDir.resolve(entityName + "FiltersTest.java");

         if (!Files.exists(testFilePath))
         {
            CompilationUnit cu = createCompilationUnit(entityClass, rootPkg, serviceClassName);
            ClassOrInterfaceDeclaration classDecl = cu.getClassByName(entityName + "FiltersTest").orElseThrow();

            for (FilterInfo filter : entityInfo.filters())
            {
               addFilterTestMethod(classDecl, filter, entityClass);
            }

            writeCompilationUnit(testFilePath, cu);
            log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would create test file: " : "Created test file: ")
                     + testFilePath.getFileName());
            return;
         }

         CompilationUnit cu = StaticJavaParser.parse(Files.readString(testFilePath, StandardCharsets.UTF_8));
         if (config.disabledByDefault())
         {
            ImportManager.add_imports(List.of("org.junit.jupiter.api.Disabled"), cu);
         }

         Optional<ClassOrInterfaceDeclaration> maybeClass = cu.getClassByName(entityName + "FiltersTest");
         if (maybeClass.isEmpty())
         {
            String message = "Class " + entityName + "FiltersTest not found in existing file " + testFilePath + ".";
            report.addDiagnostic(entityName, "invalid-existing-filter-test", "SKIPPED_INVALID_EXISTING_FILE", message);
            log.warn(Constants.QUIETLY_WARN + message);
            return;
         }

         ClassOrInterfaceDeclaration classDecl = maybeClass.get();
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

         for (FilterInfo filter : entityInfo.filters())
         {
            String methodName = toJavaIdentifier(filter.prefix + "_" + filter.field + "_filter_test");
            if (existingMethodNames.contains(methodName))
            {
               MethodDeclaration existingMethod = classDecl.getMethodsByName(methodName).get(0);
               if (ensureQuietlyMarker(existingMethod, filter))
               {
                  report.addFilter(entityName, filterName(filter), "UPDATED_MARKER",
                           "Method " + methodName + " already exists; added Quietly marker.");
               }
               else
               {
                  report.addFilter(entityName, filterName(filter), "EXISTING",
                           "Method " + methodName + " already exists.");
               }
               continue;
            }

            if (addFilterTestMethod(classDecl, filter, entityClass))
            {
               log.info(Constants.QUIETLY_INFO + "Added test: " + methodName);
            }
         }

         reportStaleGeneratedTests(classDecl, entityInfo.filters(), entityName);

         writeCompilationUnit(testFilePath, cu);
         log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would update test file: " : "Updated test file: ")
                  + testFilePath.getFileName());
      }
      finally
      {
         closeClassLoader(projectCl);
      }
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
            FilterInfo filter,
            Class<?> entityClass
   )
   {
      try
      {
         classDecl.addMember(FilterTestAstBuilder.buildFilterTestMethod(
                  filter,
                  entityClass,
                  log,
                  config.fieldResolutionMode(),
                  config.disabledByDefault()
         ));
         report.addFilter(entityClass.getSimpleName(), filterName(filter), "GENERATED", "Generated test method.");
         return true;
      }
      catch (QuietlyGenerationException e)
      {
         if (config.failOnUnresolvedField())
         {
            throw e;
         }
         String message = e.getMessage() + " Skipping this generated test because failOnUnresolvedField=false.";
         report.addFilter(entityClass.getSimpleName(), filterName(filter), "SKIPPED_UNRESOLVED_FIELD", message);
         log.warn(Constants.QUIETLY_WARN + message);
         return false;
      }
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

   private void closeClassLoader(ClassLoader classLoader) throws IOException
   {
      if (classLoader instanceof URLClassLoader urlClassLoader)
      {
         urlClassLoader.close();
      }
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

   private void reportStaleGeneratedTests(
            ClassOrInterfaceDeclaration classDecl,
            List<FilterInfo> currentFilters,
            String entityName
   )
   {
      Set<String> currentFilterNames = new HashSet<>();
      for (FilterInfo filter : currentFilters)
      {
         currentFilterNames.add(filterName(filter));
      }

      for (MethodDeclaration method : classDecl.getMethods())
      {
         Optional<String> maybeFilter = extractQuietlyGeneratedFilter(method);
         if (maybeFilter.isPresent() && !currentFilterNames.contains(maybeFilter.get()))
         {
            report.addFilter(entityName, maybeFilter.get(), "STALE_GENERATED_TEST",
                     "Generated method " + method.getNameAsString()
                              + " references a filter that was not discovered anymore.");
         }
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

package ua.quietlymavenplugin.render;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlymavenplugin.adapters.ProjectClassLoaderFactory;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
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

public class CrudTestsCodeGenerator
{

   private static final String LIST_METHOD = "list_endpoint_returns_success_test";
   private static final String GET_MISSING_METHOD = "get_missing_entity_returns_not_found_test";

   private final Log log;
   private final MavenProject project;
   private final QuietlyPluginConfig config;
   private final QuietlyReport report = new QuietlyReport(ReportType.CRUD_GENERATION);

   public CrudTestsCodeGenerator(Log log, MavenProject project, QuietlyPluginConfig config)
   {
      this.log = log;
      this.project = project;
      this.config = config;
   }

   public void generateCrudTests(List<FilterEntityInfo> entities) throws Exception
   {
      try
      {
         for (FilterEntityInfo entityInfo : entities)
         {
            generateEntityCrudTests(entityInfo.entityClass());
         }
      }
      finally
      {
         writeReport();
      }
   }

   private void generateEntityCrudTests(Class<?> scannedEntityClass) throws Exception
   {
      ClassLoader projectCl = ProjectClassLoaderFactory.buildProjectClassLoader(project);
      try
      {
         Class<?> entityClass = Class.forName(scannedEntityClass.getName(), true, projectCl);
         String entityName = entityClass.getSimpleName();
         String rootPkg = config.resolveRootPackage(entityClass);
         String serviceClassName = config.resolveServiceClassName(entityClass);

         if (!serviceExists(serviceClassName, projectCl))
         {
            String message = "Entity " + entityName + " has no matching REST service for CRUD smoke tests. "
                     + "Expected " + serviceClassName + ". Configure servicePackagePattern/serviceNamePattern "
                     + "or set failOnMissingService=false.";
            report.addDiagnostic(entityName, "missing-service", "SKIPPED_MISSING_SERVICE", message);
            if (config.failOnMissingService())
            {
               throw new QuietlyGenerationException(message);
            }
            log.warn(Constants.QUIETLY_WARN + message);
            return;
         }

         Path targetDir = config.testOutputDirectory().resolve(rootPkg.replace('.', File.separatorChar));
         Path testFilePath = targetDir.resolve(entityName + "CrudTest.java");

         if (!Files.exists(testFilePath))
         {
            CompilationUnit cu = CrudTestAstBuilder.createCompilationUnit(entityClass, rootPkg, serviceClassName,
                     config);
            report.addCrudOperation(entityName, "list", "GENERATED", "Generated list endpoint smoke test.");
            report.addCrudOperation(entityName, "get-missing", "GENERATED", "Generated missing entity smoke test.");
            writeCompilationUnit(testFilePath, cu);
            log.info(Constants.QUIETLY_INFO + (config.dryRun() ?
                     "Would create CRUD test file: " :
                     "Created CRUD test file: ")
                     + testFilePath.getFileName());
            return;
         }

         CompilationUnit cu = StaticJavaParser.parse(Files.readString(testFilePath, StandardCharsets.UTF_8));
         if (config.disabledByDefault())
         {
            ImportManager.add_imports(List.of("org.junit.jupiter.api.Disabled"), cu);
         }

         Optional<ClassOrInterfaceDeclaration> maybeClass = cu.getClassByName(entityName + "CrudTest");
         if (maybeClass.isEmpty())
         {
            String message = "Class " + entityName + "CrudTest not found in existing file " + testFilePath + ".";
            report.addDiagnostic(entityName, "invalid-existing-crud-test", "SKIPPED_INVALID_EXISTING_FILE", message);
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
            log.info(Constants.QUIETLY_INFO + "Added CRUD beforeEach for: " + entityName);
         }
         ensureCrudMethod(classDecl, existingMethodNames, LIST_METHOD, "list",
                  CrudTestAstBuilder.buildListMethod(config.disabledByDefault()));
         ensureCrudMethod(classDecl, existingMethodNames, GET_MISSING_METHOD, "get-missing",
                  CrudTestAstBuilder.buildGetMissingMethod(config.disabledByDefault()));
         reportStaleGeneratedCrudTests(classDecl, entityName);

         writeCompilationUnit(testFilePath, cu);
         log.info(Constants.QUIETLY_INFO + (config.dryRun() ?
                  "Would update CRUD test file: " :
                  "Updated CRUD test file: ")
                  + testFilePath.getFileName());
      }
      finally
      {
         closeClassLoader(projectCl);
      }
   }

   private void ensureCrudMethod(
            ClassOrInterfaceDeclaration classDecl,
            Set<String> existingMethodNames,
            String methodName,
            String operation,
            MethodDeclaration newMethod
   )
   {
      if (existingMethodNames.contains(methodName))
      {
         MethodDeclaration existingMethod = classDecl.getMethodsByName(methodName).get(0);
         if (ensureQuietlyMarker(existingMethod, operation))
         {
            report.addCrudOperation(classDecl.getNameAsString().replace("CrudTest", ""), operation, "UPDATED_MARKER",
                     "Method " + methodName + " already exists; added Quietly CRUD marker.");
         }
         else
         {
            report.addCrudOperation(classDecl.getNameAsString().replace("CrudTest", ""), operation, "EXISTING",
                     "Method " + methodName + " already exists.");
         }
         return;
      }

      classDecl.addMember(newMethod);
      report.addCrudOperation(classDecl.getNameAsString().replace("CrudTest", ""), operation, "GENERATED",
               "Generated CRUD smoke test method.");
      log.info(Constants.QUIETLY_INFO + "Added CRUD test: " + methodName);
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
      log.info(Constants.QUIETLY_INFO + "Wrote CRUD report: " + config.reportFile());
      log.info(Constants.QUIETLY_INFO + "Wrote CRUD JSON report: " + config.jsonReportFile());
   }

   private boolean ensureQuietlyMarker(MethodDeclaration method, String operation)
   {
      String marker = quietlyMarker(operation);
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

   private void reportStaleGeneratedCrudTests(ClassOrInterfaceDeclaration classDecl, String entityName)
   {
      Set<String> currentOperations = Set.of("list", "get-missing");
      for (MethodDeclaration method : classDecl.getMethods())
      {
         Optional<String> maybeOperation = extractQuietlyGeneratedCrud(method);
         if (maybeOperation.isPresent() && !currentOperations.contains(maybeOperation.get()))
         {
            report.addCrudOperation(entityName, maybeOperation.get(), "STALE_GENERATED_TEST",
                     "Generated method " + method.getNameAsString()
                              + " references a CRUD operation that is not generated anymore.");
         }
      }
   }

   private Optional<String> extractQuietlyGeneratedCrud(MethodDeclaration method)
   {
      return method.getJavadocComment()
               .map(JavadocComment::getContent)
               .flatMap(this::extractQuietlyGeneratedCrud);
   }

   private Optional<String> extractQuietlyGeneratedCrud(String javadoc)
   {
      String marker = "@quietly-generated crud=\"";
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

   private String quietlyMarker(String operation)
   {
      return "@quietly-generated crud=\"" + operation + "\"";
   }
}

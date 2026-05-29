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

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FilterTestsCodeGenerator {

   private final Log log;
   private final MavenProject project;
   private final QuietlyPluginConfig config;
   private final List<String> reportRows = new ArrayList<>();

   public FilterTestsCodeGenerator(Log log, MavenProject project) {
      this(log, project, QuietlyPluginConfig.defaults(project));
   }

   public FilterTestsCodeGenerator(Log log, MavenProject project, QuietlyPluginConfig config) {
      this.log = log;
      this.project = project;
      this.config = config;
   }

   public void generateFilterTests(List<FilterEntityInfo> entities) throws Exception {
      reportRows.clear();
      Path testRoot = config.testOutputDirectory();

      try {
         for (FilterEntityInfo entityInfo : entities) {
            generateEntityTests(entityInfo, testRoot);
         }
      } finally {
         writeReport();
      }
   }

   private void generateEntityTests(FilterEntityInfo entityInfo, Path testRoot) throws Exception {
      ClassLoader projectCl = ProjectClassLoaderFactory.buildProjectClassLoader(project);
      try {
         Class<?> entityClass = Class.forName(entityInfo.entityClass().getName(), true, projectCl);
         String entityName = entityClass.getSimpleName();
         String rootPkg = config.resolveRootPackage(entityClass);
         String serviceClassName = config.resolveServiceClassName(entityClass);

         if (!serviceExists(serviceClassName, projectCl)) {
            String message = "Entity " + entityName + " has Hibernate filters but no matching REST service was found. "
                     + "Expected " + serviceClassName + ". Configure servicePackagePattern/serviceNamePattern "
                     + "or set failOnMissingService=false.";
            addReportRow(entityName, "*", "SKIPPED_MISSING_SERVICE", message);
            if (config.failOnMissingService()) {
               throw new QuietlyGenerationException(message);
            }
            log.warn(Constants.QUIETLY_WARN + message);
            return;
         }

         Path targetDir = testRoot.resolve(rootPkg.replace('.', File.separatorChar));
         Path testFilePath = targetDir.resolve(entityName + "FiltersTest.java");

         if (!Files.exists(testFilePath)) {
            CompilationUnit cu = createCompilationUnit(entityClass, rootPkg, serviceClassName);
            ClassOrInterfaceDeclaration classDecl = cu.getClassByName(entityName + "FiltersTest").orElseThrow();

            for (FilterInfo filter : entityInfo.filters()) {
               addFilterTestMethod(classDecl, filter, entityClass);
            }

            writeCompilationUnit(testFilePath, cu);
            log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would create test file: " : "Created test file: ")
                     + testFilePath.getFileName());
            return;
         }

         CompilationUnit cu = StaticJavaParser.parse(Files.readString(testFilePath, StandardCharsets.UTF_8));
         if (config.disabledByDefault()) {
            ImportManager.add_imports(List.of("org.junit.jupiter.api.Disabled"), cu);
         }

         Optional<ClassOrInterfaceDeclaration> maybeClass = cu.getClassByName(entityName + "FiltersTest");
         if (maybeClass.isEmpty()) {
            String message = "Class " + entityName + "FiltersTest not found in existing file " + testFilePath + ".";
            addReportRow(entityName, "*", "SKIPPED_INVALID_EXISTING_FILE", message);
            log.warn(Constants.QUIETLY_WARN + message);
            return;
         }

         ClassOrInterfaceDeclaration classDecl = maybeClass.get();
         Set<String> existingMethodNames = new HashSet<>();
         for (MethodDeclaration method : classDecl.getMethods()) {
            existingMethodNames.add(method.getNameAsString());
         }

         if (!existingMethodNames.contains("beforeEach")) {
            classDecl.addMember(FilterTestAstBuilder.buildBeforeEachMethod(entityClass));
            log.info(Constants.QUIETLY_INFO + "Added method beforeEach for: " + entityName);
         }

         for (FilterInfo filter : entityInfo.filters()) {
            String methodName = toJavaIdentifier(filter.prefix + "_" + filter.field + "_filter_test");
            if (existingMethodNames.contains(methodName)) {
               MethodDeclaration existingMethod = classDecl.getMethodsByName(methodName).get(0);
               if (ensureQuietlyMarker(existingMethod, filter)) {
                  addReportRow(entityName, filterName(filter), "UPDATED_MARKER",
                           "Method " + methodName + " already exists; added Quietly marker.");
               } else {
                  addReportRow(entityName, filterName(filter), "EXISTING", "Method " + methodName + " already exists.");
               }
               continue;
            }

            if (addFilterTestMethod(classDecl, filter, entityClass)) {
               log.info(Constants.QUIETLY_INFO + "Added test: " + methodName);
            }
         }

         writeCompilationUnit(testFilePath, cu);
         log.info(Constants.QUIETLY_INFO + (config.dryRun() ? "Would update test file: " : "Updated test file: ")
                  + testFilePath.getFileName());
      } finally {
         closeClassLoader(projectCl);
      }
   }

   private CompilationUnit createCompilationUnit(Class<?> entityClass, String rootPkg, String serviceClassName) {
      String entityName = entityClass.getSimpleName();
      CompilationUnit cu = new CompilationUnit();
      cu.setPackageDeclaration(rootPkg);

      ImportManager imports = new ImportManager(cu);
      imports.add_imports(TestImportsConstants.CORE_TEST_IMPORTS);
      if (config.disabledByDefault()) {
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
      ClassOrInterfaceType serviceType = StaticJavaParser.parseClassOrInterfaceType(config.resolveServiceName(entityClass));
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
   ) {
      try {
         classDecl.addMember(FilterTestAstBuilder.buildFilterTestMethod(
                  filter,
                  entityClass,
                  log,
                  config.fieldResolutionMode(),
                  config.disabledByDefault()
         ));
         addReportRow(entityClass.getSimpleName(), filterName(filter), "GENERATED", "Generated test method.");
         return true;
      } catch (QuietlyGenerationException e) {
         if (config.failOnUnresolvedField()) {
            throw e;
         }
         String message = e.getMessage() + " Skipping this generated test because failOnUnresolvedField=false.";
         addReportRow(entityClass.getSimpleName(), filterName(filter), "SKIPPED_UNRESOLVED_FIELD", message);
         log.warn(Constants.QUIETLY_WARN + message);
         return false;
      }
   }

   private boolean serviceExists(String serviceClassName, ClassLoader projectCl) {
      try {
         Class.forName(serviceClassName, false, projectCl);
         return true;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }

   private void closeClassLoader(ClassLoader classLoader) throws IOException {
      if (classLoader instanceof URLClassLoader urlClassLoader) {
         urlClassLoader.close();
      }
   }

   private void writeCompilationUnit(Path path, CompilationUnit cu) throws IOException {
      if (config.dryRun()) {
         return;
      }

      Files.createDirectories(path.getParent());

      PrettyPrinterConfiguration conf = new PrettyPrinterConfiguration();
      conf.setIndentType(Indentation.IndentType.SPACES);
      conf.setIndentSize(4);
      conf.setPrintComments(true);
      Files.writeString(path, cu.toString(conf), StandardCharsets.UTF_8);
   }

   private void writeReport() throws IOException {
      Path reportFile = config.reportFile();
      Files.createDirectories(reportFile.getParent());

      List<String> lines = new ArrayList<>();
      lines.add("# Quietly Filter Generation Report");
      lines.add("");
      lines.add("- Generated at: `" + LocalDateTime.now() + "`");
      lines.add("- Dry run: `" + config.dryRun() + "`");
      lines.add("- Field resolution mode: `" + config.fieldResolutionMode() + "`");
      lines.add("");
      lines.add("| Entity | Filter | Status | Details |");
      lines.add("| --- | --- | --- | --- |");
      lines.addAll(reportRows);

      Files.write(reportFile, lines, StandardCharsets.UTF_8);
      log.info(Constants.QUIETLY_INFO + "Wrote report: " + reportFile);
   }

   private void addReportRow(String entity, String filter, String status, String details) {
      reportRows.add("| " + escape(entity) + " | " + escape(filter) + " | " + escape(status) + " | "
               + escape(details) + " |");
   }

   private String filterName(FilterInfo filter) {
      return filter.prefix + "." + filter.field;
   }

   private boolean ensureQuietlyMarker(MethodDeclaration method, FilterInfo filter) {
      String marker = quietlyMarker(filter);
      if (method.getJavadocComment()
               .map(JavadocComment::getContent)
               .map(content -> content.contains(marker))
               .orElse(false)) {
         return false;
      }

      method.setJavadocComment(marker);
      return true;
   }

   private String quietlyMarker(FilterInfo filter) {
      return "@quietly-generated filter=\"" + filterName(filter) + "\"";
   }

   private String escape(String value) {
      return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
   }

   private String toJavaIdentifier(String value) {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (i == 0) {
            result.append(Character.isJavaIdentifierStart(c) ? c : '_');
         } else {
            result.append(Character.isJavaIdentifierPart(c) ? c : '_');
         }
      }
      return result.toString();
   }
}

package ua.quietlymavenplugin.render.report;

import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

public class QuietlyReport
{

   private static final Set<String> GENERATED_STATUSES =
            Set.of("GENERATED", "EXISTING", "UPDATED_MARKER");
   private static final Set<String> GENERATABLE_STATUSES =
            Set.of("GENERATED", "EXISTING", "UPDATED_MARKER", "WOULD_GENERATE");
   private static final Set<String> READY_STATUSES = Set.of("OK");
   private static final String EXECUTION_STATUS = "NOT_MEASURED";
   private static final String EXECUTION_DETAILS =
            "Quietly does not measure whether generated tests passed. Run Maven/Surefire and inspect its test results.";

   private final ReportType type;
   private final List<QuietlyReportEntry> entries = new ArrayList<>();

   public QuietlyReport(ReportType type)
   {
      this.type = type;
   }

   public ReportType type()
   {
      return type;
   }

   public void addFilter(String entity, String subject, String status, String details)
   {
      add(entity, ReportCapability.FILTER_TEST, subject, status, details);
   }

   public void addCrudOperation(String entity, String subject, String status, String details)
   {
      add(entity, ReportCapability.CRUD_OPERATION, subject, status, details);
   }

   public void addDiagnostic(String entity, String subject, String status, String details)
   {
      add(entity, ReportCapability.DIAGNOSTIC, subject, status, details);
   }

   public void add(
            String entity,
            ReportCapability capability,
            String subject,
            String status,
            String details
   )
   {
      entries.add(new QuietlyReportEntry(entity, capability, subject, status, details));
   }

   public List<QuietlyReportEntry> entries()
   {
      return List.copyOf(entries);
   }

   public long count(String status)
   {
      return entries.stream().filter(entry -> entry.status().equals(status)).count();
   }

   public long discoveredFilters()
   {
      return logicalCount(ReportCapability.FILTER_TEST, entry -> !"STALE_GENERATED_TEST".equals(entry.status()));
   }

   public long totalFilters()
   {
      return discoveredFilters();
   }

   public long coveredFilters()
   {
      return switch (type)
      {
         case PROJECT_DIAGNOSTICS -> readyFilters();
         case FILTER_GENERATION -> matchingLogicalCount(ReportCapability.FILTER_TEST, GENERATABLE_STATUSES);
         case FILTER_SCAN, CRUD_GENERATION -> 0;
      };
   }

   public long generatedFilters()
   {
      return matchingLogicalCount(ReportCapability.FILTER_TEST, GENERATED_STATUSES);
   }

   public long readyFilters()
   {
      return matchingLogicalCount(ReportCapability.FILTER_TEST, READY_STATUSES);
   }

   public long generatableFilters()
   {
      return switch (type)
      {
         case PROJECT_DIAGNOSTICS -> readyFilters();
         case FILTER_GENERATION -> matchingLogicalCount(ReportCapability.FILTER_TEST, GENERATABLE_STATUSES);
         case FILTER_SCAN, CRUD_GENERATION -> 0;
      };
   }

   public long blockedFilters()
   {
      return logicalCount(
               ReportCapability.FILTER_TEST,
               entry -> !"STALE_GENERATED_TEST".equals(entry.status()) && isProblemStatus(entry.status())
      );
   }

   public long generatedTestClasses()
   {
      return entries.stream()
               .filter(entry -> entry.capability() == ReportCapability.FILTER_TEST)
               .filter(entry -> GENERATED_STATUSES.contains(entry.status()))
               .map(QuietlyReportEntry::entity)
               .distinct()
               .count();
   }

   public long generatedTestMethods()
   {
      return generatedFilters();
   }

   public long totalCrudOperations()
   {
      return logicalCount(ReportCapability.CRUD_OPERATION, entry -> !"STALE_GENERATED_TEST".equals(entry.status()));
   }

   public long coveredCrudOperations()
   {
      return matchingLogicalCount(ReportCapability.CRUD_OPERATION, GENERATED_STATUSES);
   }

   public long diagnostics()
   {
      return logicalCount(ReportCapability.DIAGNOSTIC, entry -> true);
   }

   public double coveragePercent()
   {
      return percentage(coveredFilters(), totalFilters());
   }

   public double generationCoveragePercent()
   {
      return percentage(generatedFilters(), totalFilters());
   }

   public double readinessPercent()
   {
      return percentage(readyFilters(), totalFilters());
   }

   public double generationReadinessPercent()
   {
      return percentage(generatableFilters(), discoveredFilters());
   }

   public double crudCoveragePercent()
   {
      return percentage(coveredCrudOperations(), totalCrudOperations());
   }

   public boolean hasProblems()
   {
      return problems() > 0;
   }

   public long problems()
   {
      return entries.stream()
               .filter(entry -> isProblemStatus(entry.status()))
               .map(QuietlyReportEntry::logicalKey)
               .distinct()
               .count();
   }

   public void write(QuietlyPluginConfig config) throws IOException
   {
      writeMarkdown(config.reportFile(), config);
      writeJson(config.jsonReportFile(), config);
   }

   private long logicalCount(ReportCapability capability, Predicate<QuietlyReportEntry> predicate)
   {
      return entries.stream()
               .filter(entry -> entry.capability() == capability)
               .filter(predicate)
               .map(QuietlyReportEntry::logicalKey)
               .distinct()
               .count();
   }

   private long matchingLogicalCount(ReportCapability capability, Set<String> matchingStatuses)
   {
      Map<String, Boolean> coverageByKey = new LinkedHashMap<>();
      entries.stream()
               .filter(entry -> entry.capability() == capability)
               .filter(entry -> !"STALE_GENERATED_TEST".equals(entry.status()))
               .forEach(entry -> coverageByKey.merge(
                        entry.logicalKey(),
                        matchingStatuses.contains(entry.status()),
                        Boolean::logicalOr
               ));
      return coverageByKey.values().stream().filter(Boolean::booleanValue).count();
   }

   private double percentage(long covered, long total)
   {
      return total == 0 ? 0.0 : covered * 100.0 / total;
   }

   private void writeMarkdown(Path reportFile, QuietlyPluginConfig config) throws IOException
   {
      Files.createDirectories(reportFile.getParent());

      List<String> lines = new ArrayList<>();
      lines.add("# " + type.title());
      lines.add("");
      lines.add("- Generated at: `" + LocalDateTime.now() + "`");
      lines.add("- Dry run: `" + config.dryRun() + "`");
      lines.add("- Field resolution mode: `" + config.fieldResolutionMode() + "`");
      lines.add("- Execution: `" + EXECUTION_STATUS + "`");
      lines.add("  " + EXECUTION_DETAILS);
      lines.add("");
      lines.add("## Summary");
      lines.add("");
      addMarkdownSummary(lines);
      lines.add("- Generated events: `" + count("GENERATED") + "`");
      lines.add("- Would generate events: `" + count("WOULD_GENERATE") + "`");
      lines.add("- Existing events: `" + count("EXISTING") + "`");
      lines.add("- Updated markers: `" + count("UPDATED_MARKER") + "`");
      lines.add("- Stale generated tests: `" + count("STALE_GENERATED_TEST") + "`");
      lines.add("");
      lines.add("## Details");
      lines.add("");
      lines.add("| Entity | Capability | Subject | Status | Details |");
      lines.add("| --- | --- | --- | --- | --- |");
      for (QuietlyReportEntry entry : entries)
      {
         lines.add("| " + escapeMarkdown(entry.entity()) + " | " + entry.capability() + " | "
                  + escapeMarkdown(entry.subject()) + " | " + escapeMarkdown(entry.status()) + " | "
                  + escapeMarkdown(entry.details()) + " |");
      }

      Files.write(reportFile, lines, StandardCharsets.UTF_8);
   }

   private void writeJson(Path reportFile, QuietlyPluginConfig config) throws IOException
   {
      Files.createDirectories(reportFile.getParent());

      StringBuilder json = new StringBuilder();
      json.append("{\n");
      json.append("  \"reportType\": \"").append(type).append("\",\n");
      json.append("  \"dryRun\": ").append(config.dryRun()).append(",\n");
      json.append("  \"fieldResolutionMode\": \"").append(config.fieldResolutionMode()).append("\",\n");
      json.append("  \"summary\": {\n");
      appendJsonSummary(json);
      json.append("  },\n");
      json.append("  \"execution\": {\n");
      json.append("    \"status\": \"").append(EXECUTION_STATUS).append("\",\n");
      json.append("    \"measuredByQuietly\": false,\n");
      json.append("    \"details\": \"").append(escapeJson(EXECUTION_DETAILS)).append("\"\n");
      json.append("  },\n");
      json.append("  \"entries\": [\n");
      for (int i = 0; i < entries.size(); i++)
      {
         QuietlyReportEntry entry = entries.get(i);
         json.append("    {");
         json.append("\"entity\": \"").append(escapeJson(entry.entity())).append("\", ");
         json.append("\"capability\": \"").append(entry.capability()).append("\", ");
         json.append("\"subject\": \"").append(escapeJson(entry.subject())).append("\", ");
         json.append("\"status\": \"").append(escapeJson(entry.status())).append("\", ");
         json.append("\"details\": \"").append(escapeJson(entry.details())).append("\"");
         json.append("}");
         if (i < entries.size() - 1)
         {
            json.append(",");
         }
         json.append("\n");
      }
      json.append("  ]\n");
      json.append("}\n");

      Files.writeString(reportFile, json.toString(), StandardCharsets.UTF_8);
   }

   private void appendJsonSummary(StringBuilder json)
   {
      switch (type)
      {
      case FILTER_SCAN ->
      {
         json.append("    \"discoveredFilters\": ").append(discoveredFilters()).append(",\n");
         json.append("    \"generatableFilters\": null,\n");
         json.append("    \"blockedFilters\": null,\n");
         json.append("    \"generationReadinessPercent\": null,\n");
         appendJsonGeneratedTestSummary(json);
         appendJsonEventSummary(json);
      }
      case PROJECT_DIAGNOSTICS ->
      {
         appendJsonFilterReadinessSummary(json);
         json.append("    \"analyzedFilters\": ").append(totalFilters()).append(",\n");
         json.append("    \"readyFilters\": ").append(readyFilters()).append(",\n");
         json.append("    \"readinessPercent\": ").append(format(readinessPercent())).append(",\n");
         json.append("    \"existingGeneratedTests\": ").append(generatedFilters()).append(",\n");
         json.append("    \"generationCoveragePercent\": ").append(format(generationCoveragePercent())).append(",\n");
         json.append("    \"diagnostics\": ").append(diagnostics()).append(",\n");
         json.append("    \"problems\": ").append(problems()).append(",\n");
         appendJsonEventSummary(json);
      }
      case FILTER_GENERATION ->
      {
         appendJsonFilterReadinessSummary(json);
         json.append("    \"totalFilters\": ").append(totalFilters()).append(",\n");
         json.append("    \"coveredFilters\": ").append(generatedFilters()).append(",\n");
         json.append("    \"coveragePercent\": ").append(format(generationCoveragePercent())).append(",\n");
         json.append("    \"generationCoveragePercent\": ").append(format(generationCoveragePercent())).append(",\n");
         json.append("    \"filterCoveragePercent\": ").append(format(generationCoveragePercent())).append(",\n");
         json.append("    \"diagnostics\": ").append(diagnostics()).append(",\n");
         json.append("    \"problems\": ").append(problems()).append(",\n");
         appendJsonEventSummary(json);
      }
      case CRUD_GENERATION ->
      {
         json.append("    \"crudOperations\": ").append(totalCrudOperations()).append(",\n");
         json.append("    \"coveredCrudOperations\": ").append(coveredCrudOperations()).append(",\n");
         json.append("    \"crudCoveragePercent\": ").append(format(crudCoveragePercent())).append(",\n");
         json.append("    \"diagnostics\": ").append(diagnostics()).append(",\n");
         json.append("    \"problems\": ").append(problems()).append(",\n");
         appendJsonEventSummary(json);
      }
      }
   }

   private void appendJsonEventSummary(StringBuilder json)
   {
      json.append("    \"generatedEvents\": ").append(count("GENERATED")).append(",\n");
      json.append("    \"wouldGenerateEvents\": ").append(count("WOULD_GENERATE")).append(",\n");
      json.append("    \"existingEvents\": ").append(count("EXISTING")).append(",\n");
      json.append("    \"updatedMarkers\": ").append(count("UPDATED_MARKER")).append(",\n");
      json.append("    \"staleGeneratedTests\": ").append(count("STALE_GENERATED_TEST")).append("\n");
   }

   private void appendJsonFilterReadinessSummary(StringBuilder json)
   {
      json.append("    \"discoveredFilters\": ").append(discoveredFilters()).append(",\n");
      json.append("    \"generatableFilters\": ").append(generatableFilters()).append(",\n");
      json.append("    \"blockedFilters\": ").append(blockedFilters()).append(",\n");
      json.append("    \"generationReadinessPercent\": ").append(format(generationReadinessPercent())).append(",\n");
      appendJsonGeneratedTestSummary(json);
   }

   private void appendJsonGeneratedTestSummary(StringBuilder json)
   {
      json.append("    \"generatedTestClasses\": ").append(generatedTestClasses()).append(",\n");
      json.append("    \"generatedTestMethods\": ").append(generatedTestMethods()).append(",\n");
   }

   private void addMarkdownSummary(List<String> lines)
   {
      switch (type)
      {
      case FILTER_SCAN ->
      {
         lines.add("- Discovered filters: `" + discoveredFilters() + "`");
         lines.add("- Generatable filters: `not evaluated by scan`");
         lines.add("- Blocked/ambiguous filters: `not evaluated by scan`");
         lines.add("- Generation readiness: `not evaluated by scan`");
         addMarkdownGeneratedTestSummary(lines);
      }
      case PROJECT_DIAGNOSTICS ->
      {
         addMarkdownFilterReadinessSummary(lines);
         lines.add("- Diagnostics: `" + diagnostics() + "`");
         lines.add("- Problems: `" + problems() + "`");
      }
      case FILTER_GENERATION ->
      {
         addMarkdownFilterReadinessSummary(lines);
         lines.add("- Diagnostics: `" + diagnostics() + "`");
         lines.add("- Problems: `" + problems() + "`");
      }
      case CRUD_GENERATION ->
      {
         lines.add("- CRUD operations: `" + totalCrudOperations() + "`");
         lines.add("- Covered CRUD operations: `" + coveredCrudOperations() + "`");
         lines.add("- CRUD coverage: `" + format(crudCoveragePercent()) + "%`");
         lines.add("- Diagnostics: `" + diagnostics() + "`");
         lines.add("- Problems: `" + problems() + "`");
      }
      }
   }

   private void addMarkdownFilterReadinessSummary(List<String> lines)
   {
      lines.add("- Discovered filters: `" + discoveredFilters() + "`");
      lines.add("- Generatable filters: `" + generatableFilters() + "`");
      lines.add("- Blocked/ambiguous filters: `" + blockedFilters() + "`");
      lines.add("- Generation readiness: `" + format(generationReadinessPercent()) + "%`");
      addMarkdownGeneratedTestSummary(lines);
   }

   private void addMarkdownGeneratedTestSummary(List<String> lines)
   {
      lines.add("- Generated test classes: `" + generatedTestClasses() + "`");
      lines.add("- Generated test methods: `" + generatedTestMethods() + "`");
   }

   private boolean isProblemStatus(String status)
   {
      return status.startsWith("SKIPPED")
               || status.startsWith("MISSING")
               || status.startsWith("STALE")
               || status.startsWith("ERROR");
   }

   private String format(double value)
   {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private String escapeMarkdown(String value)
   {
      return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
   }

   private String escapeJson(String value)
   {
      if (value == null)
      {
         return "";
      }
      return value
               .replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "\\r");
   }
}

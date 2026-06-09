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
   private static final Set<String> READY_STATUSES = Set.of("OK");

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
         case FILTER_GENERATION -> generatedFilters();
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
               .filter(entry -> entry.status().startsWith("SKIPPED")
                        || entry.status().startsWith("MISSING")
                        || entry.status().startsWith("STALE")
                        || entry.status().startsWith("ERROR"))
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
      lines.add("");
      lines.add("## Summary");
      lines.add("");
      addMarkdownSummary(lines);
      lines.add("- Generated events: `" + count("GENERATED") + "`");
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
         appendJsonEventSummary(json);
      }
      case PROJECT_DIAGNOSTICS ->
      {
         json.append("    \"analyzedFilters\": ").append(totalFilters()).append(",\n");
         json.append("    \"readyFilters\": ").append(readyFilters()).append(",\n");
         json.append("    \"readinessPercent\": ").append(format(readinessPercent())).append(",\n");
         json.append("    \"existingGeneratedTests\": ").append(generatedFilters()).append(",\n");
         json.append("    \"diagnostics\": ").append(diagnostics()).append(",\n");
         json.append("    \"problems\": ").append(problems()).append(",\n");
         appendJsonEventSummary(json);
      }
      case FILTER_GENERATION ->
      {
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
      json.append("    \"existingEvents\": ").append(count("EXISTING")).append(",\n");
      json.append("    \"updatedMarkers\": ").append(count("UPDATED_MARKER")).append(",\n");
      json.append("    \"staleGeneratedTests\": ").append(count("STALE_GENERATED_TEST")).append("\n");
   }

   private void addMarkdownSummary(List<String> lines)
   {
      switch (type)
      {
      case FILTER_SCAN -> lines.add("- Discovered filters: `" + discoveredFilters() + "`");
      case PROJECT_DIAGNOSTICS ->
      {
         lines.add("- Analyzed filters: `" + totalFilters() + "`");
         lines.add("- Ready filters: `" + readyFilters() + "`");
         lines.add("- Readiness: `" + format(readinessPercent()) + "%`");
         lines.add("- Existing generated tests: `" + generatedFilters() + "`");
         lines.add("- Diagnostics: `" + diagnostics() + "`");
         lines.add("- Problems: `" + problems() + "`");
      }
      case FILTER_GENERATION ->
      {
         lines.add("- Total filters: `" + totalFilters() + "`");
         lines.add("- Covered filters: `" + generatedFilters() + "`");
         lines.add("- Generation coverage: `" + format(generationCoveragePercent()) + "%`");
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

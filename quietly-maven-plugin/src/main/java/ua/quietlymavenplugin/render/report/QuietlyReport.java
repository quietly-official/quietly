package ua.quietlymavenplugin.render.report;

import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuietlyReport {

   private final List<QuietlyReportEntry> entries = new ArrayList<>();

   public void add(String entity, String filter, String status, String details) {
      entries.add(new QuietlyReportEntry(entity, filter, status, details));
   }

   public List<QuietlyReportEntry> entries() {
      return List.copyOf(entries);
   }

   public long count(String status) {
      return entries.stream().filter(entry -> entry.status().equals(status)).count();
   }

   public long totalFilters() {
      return entries.stream().filter(entry -> !"*".equals(entry.filter())).count();
   }

   public long coveredFilters() {
      return entries.stream()
               .filter(entry -> entry.status().equals("GENERATED")
                        || entry.status().equals("EXISTING")
                        || entry.status().equals("UPDATED_MARKER"))
               .count();
   }

   public double coveragePercent() {
      long total = totalFilters();
      if (total == 0) {
         return 100.0;
      }
      return coveredFilters() * 100.0 / total;
   }

   public boolean hasProblems() {
      return entries.stream().anyMatch(entry -> entry.status().startsWith("SKIPPED")
               || entry.status().startsWith("MISSING")
               || entry.status().startsWith("STALE")
               || entry.status().startsWith("ERROR"));
   }

   public void write(QuietlyPluginConfig config) throws IOException {
      writeMarkdown(config.reportFile(), config);
      writeJson(config.jsonReportFile(), config);
   }

   private void writeMarkdown(Path reportFile, QuietlyPluginConfig config) throws IOException {
      Files.createDirectories(reportFile.getParent());

      List<String> lines = new ArrayList<>();
      lines.add("# Quietly Filter Generation Report");
      lines.add("");
      lines.add("- Generated at: `" + LocalDateTime.now() + "`");
      lines.add("- Dry run: `" + config.dryRun() + "`");
      lines.add("- Field resolution mode: `" + config.fieldResolutionMode() + "`");
      lines.add("");
      lines.add("## Summary");
      lines.add("");
      lines.add("- Total filters: `" + totalFilters() + "`");
      lines.add("- Covered filters: `" + coveredFilters() + "`");
      lines.add("- Coverage: `" + String.format("%.2f", coveragePercent()) + "%`");
      lines.add("- Generated: `" + count("GENERATED") + "`");
      lines.add("- Existing: `" + count("EXISTING") + "`");
      lines.add("- Updated markers: `" + count("UPDATED_MARKER") + "`");
      lines.add("- Stale generated tests: `" + count("STALE_GENERATED_TEST") + "`");
      lines.add("");
      lines.add("## Details");
      lines.add("");
      lines.add("| Entity | Filter | Status | Details |");
      lines.add("| --- | --- | --- | --- |");
      for (QuietlyReportEntry entry : entries) {
         lines.add("| " + escapeMarkdown(entry.entity()) + " | " + escapeMarkdown(entry.filter()) + " | "
                  + escapeMarkdown(entry.status()) + " | " + escapeMarkdown(entry.details()) + " |");
      }

      Files.write(reportFile, lines, StandardCharsets.UTF_8);
   }

   private void writeJson(Path reportFile, QuietlyPluginConfig config) throws IOException {
      Files.createDirectories(reportFile.getParent());

      StringBuilder json = new StringBuilder();
      json.append("{\n");
      json.append("  \"dryRun\": ").append(config.dryRun()).append(",\n");
      json.append("  \"fieldResolutionMode\": \"").append(config.fieldResolutionMode()).append("\",\n");
      json.append("  \"summary\": {\n");
      json.append("    \"totalFilters\": ").append(totalFilters()).append(",\n");
      json.append("    \"coveredFilters\": ").append(coveredFilters()).append(",\n");
      json.append("    \"coveragePercent\": ").append(String.format(java.util.Locale.ROOT, "%.2f", coveragePercent())).append(",\n");
      json.append("    \"generated\": ").append(count("GENERATED")).append(",\n");
      json.append("    \"existing\": ").append(count("EXISTING")).append(",\n");
      json.append("    \"updatedMarkers\": ").append(count("UPDATED_MARKER")).append(",\n");
      json.append("    \"staleGeneratedTests\": ").append(count("STALE_GENERATED_TEST")).append("\n");
      json.append("  },\n");
      json.append("  \"entries\": [\n");
      for (int i = 0; i < entries.size(); i++) {
         QuietlyReportEntry entry = entries.get(i);
         json.append("    {");
         json.append("\"entity\": \"").append(escapeJson(entry.entity())).append("\", ");
         json.append("\"filter\": \"").append(escapeJson(entry.filter())).append("\", ");
         json.append("\"status\": \"").append(escapeJson(entry.status())).append("\", ");
         json.append("\"details\": \"").append(escapeJson(entry.details())).append("\"");
         json.append("}");
         if (i < entries.size() - 1) {
            json.append(",");
         }
         json.append("\n");
      }
      json.append("  ]\n");
      json.append("}\n");

      Files.writeString(reportFile, json.toString(), StandardCharsets.UTF_8);
   }

   private String escapeMarkdown(String value) {
      return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
   }

   private String escapeJson(String value) {
      if (value == null) {
         return "";
      }
      return value
               .replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "\\r");
   }
}

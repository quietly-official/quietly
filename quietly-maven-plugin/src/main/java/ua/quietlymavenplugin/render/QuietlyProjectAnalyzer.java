package ua.quietlymavenplugin.render;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import ua.quietlymavenplugin.discovery.DiscoveredProject;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.plan.DoctorPlanner;
import ua.quietlymavenplugin.plan.GenerationPlan;
import ua.quietlymavenplugin.plan.PlanEntry;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;
import ua.quietlymavenplugin.render.report.QuietlyReport;
import ua.quietlymavenplugin.render.report.ReportType;

import java.util.List;

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
      return doctor(new DiscoveredProject(config.moduleContext(), entities));
   }

   public QuietlyReport doctor(DiscoveredProject discoveredProject) throws Exception
   {
      QuietlyReport report = new QuietlyReport(ReportType.PROJECT_DIAGNOSTICS);
      GenerationPlan plan = new DoctorPlanner(project, config).plan(discoveredProject);
      for (PlanEntry entry : plan.entries())
      {
         addPlanEntry(report, entry);
      }
      report.write(config);
      return report;
   }

   private void addPlanEntry(QuietlyReport report, PlanEntry entry)
   {
      String entityName = entry.entity().getSimpleName();
      if (entry.diagnostic())
      {
         if (isGeneratedTestState(entry.reportStatus()))
         {
            report.addFilter(entityName, entry.subject(), entry.reportStatus(), entry.reason());
         }
         else
         {
            report.addDiagnostic(entityName, entry.subject(), entry.reportStatus(), entry.reason());
         }
         return;
      }

      report.addFilter(entityName, entry.subject(), entry.reportStatus(), entry.reason());
   }

   private boolean isGeneratedTestState(String status)
   {
      return "EXISTING".equals(status) || "STALE_GENERATED_TEST".equals(status);
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
         log.info(Constants.QUIETLY_INFO + "Generation readiness: "
                  + String.format(java.util.Locale.ROOT, "%.2f", report.generationReadinessPercent()) + "%");
         log.info(Constants.QUIETLY_INFO + "Generated test methods found: " + report.generatedTestMethods());
         log.info(Constants.QUIETLY_INFO + "Execution: not measured by Quietly");
         log.info(Constants.QUIETLY_INFO + "Problems: " + report.problems());
      }
   }
}

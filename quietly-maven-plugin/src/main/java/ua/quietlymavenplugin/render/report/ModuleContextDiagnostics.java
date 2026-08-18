package ua.quietlymavenplugin.render.report;

import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

public final class ModuleContextDiagnostics
{

   public static final String AGGREGATOR_WARNING_STATUS = "WARNING_AGGREGATOR_MODULE";
   public static final String AGGREGATOR_WARNING_MESSAGE =
            "Quietly is running on a Maven aggregator module with packaging=pom. Quietly is not currently a reactor "
                     + "aggregator plugin. Configure it in the concrete application/test module that owns the Quarkus "
                     + "test runtime and can see entities/services on its classpath.";

   private ModuleContextDiagnostics()
   {
   }

   public static boolean addAggregatorWarningIfNeeded(QuietlyReport report, QuietlyPluginConfig config)
   {
      if (!config.pomPackaging())
      {
         return false;
      }

      report.addDiagnostic(
               config.moduleArtifactId(),
               "maven-module-context",
               AGGREGATOR_WARNING_STATUS,
               AGGREGATOR_WARNING_MESSAGE
      );
      return true;
   }
}

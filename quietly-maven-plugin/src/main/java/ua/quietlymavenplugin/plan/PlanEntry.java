package ua.quietlymavenplugin.plan;

import ua.quietlycore.model.FilterInfo;
import ua.quietlymavenplugin.discovery.FixtureResolution;
import ua.quietlymavenplugin.discovery.ServiceResolution;
import ua.quietlymavenplugin.render.javaparser.FieldResolutionResult;

import java.util.Optional;

public record PlanEntry(
         Class<?> entity,
         Optional<FilterInfo> filter,
         String subject,
         GenerationCapability capability,
         PlanState state,
         String reportStatus,
         String reason,
         boolean diagnostic,
         Optional<ServiceResolution> serviceResolution,
         Optional<FieldResolutionResult> fieldResolution,
         Optional<FixtureResolution> fixtureResolution
)
{

   public PlanEntry
   {
      filter = filter == null ? Optional.empty() : filter;
      serviceResolution = serviceResolution == null ? Optional.empty() : serviceResolution;
      fieldResolution = fieldResolution == null ? Optional.empty() : fieldResolution;
      fixtureResolution = fixtureResolution == null ? Optional.empty() : fixtureResolution;
   }

   public static PlanEntry filter(
            Class<?> entity,
            FilterInfo filter,
            PlanState state,
            String reportStatus,
            String reason,
            ServiceResolution serviceResolution,
            FieldResolutionResult fieldResolution
   )
   {
      return new PlanEntry(
               entity,
               Optional.of(filter),
               filter.prefix + "." + filter.field,
               GenerationCapability.FILTER_TEST,
               state,
               reportStatus,
               reason,
               false,
               Optional.ofNullable(serviceResolution),
               Optional.ofNullable(fieldResolution),
               Optional.empty()
      );
   }

   public static PlanEntry diagnostic(
            Class<?> entity,
            String subject,
            PlanState state,
            String reportStatus,
            String reason,
            ServiceResolution serviceResolution,
            FixtureResolution fixtureResolution
   )
   {
      return new PlanEntry(
               entity,
               Optional.empty(),
               subject,
               GenerationCapability.FILTER_TEST,
               state,
               reportStatus,
               reason,
               true,
               Optional.ofNullable(serviceResolution),
               Optional.empty(),
               Optional.ofNullable(fixtureResolution)
      );
   }
}

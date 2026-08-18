package ua.quietlymavenplugin.plan;

import java.util.List;

public record GenerationPlan(List<PlanEntry> entries)
{

   public GenerationPlan
   {
      entries = List.copyOf(entries);
   }
}

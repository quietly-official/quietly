package ua.quietlymavenplugin.discovery;

import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlymavenplugin.render.config.ModuleContext;

import java.util.List;

public record DiscoveredProject(ModuleContext moduleContext, List<FilterEntityInfo> entities)
{

   public DiscoveredProject
   {
      entities = List.copyOf(entities);
   }
}

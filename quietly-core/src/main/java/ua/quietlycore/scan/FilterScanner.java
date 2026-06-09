package ua.quietlycore.scan;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.model.FilterInfo;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class FilterScanner
{

   public static List<FilterEntityInfo> scanProjectEntities(
            List<String> classpathElements,
            String outputDir
   ) throws Exception
   {
      return scanProjectEntities(
               classpathElements,
               outputDir,
               EntityScanOptions.allApplicationEntities(null)
      );
   }

   public static List<FilterEntityInfo> scanProjectEntities(
            List<String> classpathElements,
            String outputDir,
            EntityScanOptions options
   ) throws Exception
   {
      URL projectOutputUrl = new File(outputDir).toURI().toURL();
      List<URL> classLoaderUrls = new ArrayList<>();
      classLoaderUrls.add(projectOutputUrl);

      for (String element : classpathElements)
      {
         URL url = new File(element).toURI().toURL();
         if (!classLoaderUrls.contains(url))
         {
            classLoaderUrls.add(url);
         }
      }

      URLClassLoader projectCl = new URLClassLoader(
               classLoaderUrls.toArray(new URL[0]),
               Thread.currentThread().getContextClassLoader()
      );

      ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(projectCl);

      try
      {
         Reflections reflections = new Reflections(
                  new ConfigurationBuilder()
                           .setUrls(projectOutputUrl)
                           .addClassLoaders(projectCl)
                           .setScanners(Scanners.TypesAnnotated)
         );

         Set<Class<?>> entities = reflections.getTypesAnnotatedWith(Entity.class);

         List<FilterEntityInfo> result = new ArrayList<>();

         for (Class<?> entity : entities)
         {
            if (!options.matchesPackage(entity))
            {
               continue;
            }
            List<FilterInfo> filters = scanEntity(entity);
            if (options.requireHibernateFilters() && filters.isEmpty())
            {
               continue;
            }
            result.add(new FilterEntityInfo(entity, filters));
         }

         result.sort(Comparator.comparing(info -> info.entityClass().getName()));
         return result;

      }
      finally
      {
         Thread.currentThread().setContextClassLoader(oldCl);
         projectCl.close();
      }
   }

   public static List<FilterInfo> scanEntity(Class<?> entityClass)
   {

      Map<String, FilterInfo> map = new HashMap<>();

      Filter singleFilter = entityClass.getAnnotation(Filter.class);
      if (singleFilter != null)
      {
         mergeFilter(map, singleFilter, entityClass);
      }

      org.hibernate.annotations.Filters fs =
               entityClass.getAnnotation(org.hibernate.annotations.Filters.class);
      if (fs != null)
      {
         for (Filter f : fs.value())
         {
            mergeFilter(map, f, entityClass);
         }
      }

      FilterDef fd = entityClass.getAnnotation(FilterDef.class);
      if (fd != null)
      {
         mergeFilterDef(map, fd, entityClass);
      }

      org.hibernate.annotations.FilterDefs fds =
               entityClass.getAnnotation(org.hibernate.annotations.FilterDefs.class);
      if (fds != null)
      {
         for (FilterDef def : fds.value())
         {
            mergeFilterDef(map, def, entityClass);
         }
      }

      return map.values().stream()
               .sorted(Comparator.comparing(filter -> filter.prefix + "." + filter.field))
               .toList();
   }

   private static void mergeFilter(Map<String, FilterInfo> map, Filter f, Class<?> entity)
   {
      String normalized = normalizeFilterName(entity, f.name());

      FilterNameParts parts = splitFilterName(normalized);
      if (parts == null)
      {
         throw new IllegalStateException("Filtro non valido: " + f.name());
      }

      String prefix = parts.prefix();
      String field = parts.field();

      FilterInfo fi = map.computeIfAbsent(normalized, k -> {
         FilterInfo x = new FilterInfo();
         x.prefix = prefix;
         x.field = field;
         return x;
      });

      fi.condition = f.condition();
   }

   private static void mergeFilterDef(Map<String, FilterInfo> map, FilterDef fd, Class<?> entity)
   {
      String normalized = normalizeFilterName(entity, fd.name());

      FilterNameParts parts = splitFilterName(normalized);
      if (parts == null)
      {
         throw new IllegalStateException("FilterDef non valido: " + fd.name());
      }

      String prefix = parts.prefix();
      String field = parts.field();

      FilterInfo fi = map.computeIfAbsent(normalized, k -> {
         FilterInfo x = new FilterInfo();
         x.prefix = prefix;
         x.field = field;
         return x;
      });

      if (fd.parameters().length > 0)
      {
         var p = fd.parameters()[0];
         fi.paramName = p.name();
         fi.paramType = p.type();
      }
      else
      {
         fi.paramType = null;
      }
   }

   private static String normalizeFilterName(Class<?> entity, String name)
   {
      String entityPrefix = entity.getSimpleName() + ".";
      if (name.startsWith(entityPrefix))
      {
         return name.substring(entityPrefix.length());
      }
      return name;
   }

   private static FilterNameParts splitFilterName(String normalized)
   {
      int lastDot = normalized.lastIndexOf('.');
      if (lastDot <= 0 || lastDot == normalized.length() - 1)
      {
         return null;
      }
      return new FilterNameParts(normalized.substring(0, lastDot), normalized.substring(lastDot + 1));
   }

   private record FilterNameParts(String prefix, String field)
   {
   }

}

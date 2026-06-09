package ua.quietlymavenplugin.render.javaparser;

import org.apache.maven.plugin.logging.Log;
import ua.quietlymavenplugin.render.config.Constants;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serve per risolvere i campi nelle entity, anche fuzzy
 */
public class FieldResolver
{

   public static FieldResolutionResult resolveField(Class<?> clazz, String filterField, FieldResolutionMode mode)
   {
      FieldResolutionMode effectiveMode = mode == null ? FieldResolutionMode.STRICT : mode;
      return switch (effectiveMode)
      {
         case STRICT -> resolveStrict(clazz, filterField);
         case FUZZY -> resolveFuzzy(clazz, filterField);
      };
   }

   public static Field resolveFieldFuzzy(Class<?> clazz, String filterField, Log log)
   {
      FieldResolutionResult result = resolveField(clazz, filterField, FieldResolutionMode.FUZZY);
      result.warnings().forEach(w -> log.warn(Constants.QUIETLY_WARN + w));
      if (result.resolved())
      {
         return result.field().orElseThrow();
      }
      throw new RuntimeException(String.join("; ", result.errors()));
   }

   private static FieldResolutionResult resolveStrict(Class<?> clazz, String filterField)
   {
      List<Field> exactMatches = getFieldsFromHierarchy(clazz, filterField);
      if (exactMatches.size() == 1)
      {
         Field field = exactMatches.get(0);
         field.setAccessible(true);
         return new FieldResolutionResult(field, List.of(), List.of(), filterField, FieldResolutionMode.STRICT);
      }

      if (exactMatches.size() > 1)
      {
         return new FieldResolutionResult(
                  null,
                  List.of(),
                  List.of("Ambiguous field '" + filterField + "' on entity " + clazz.getSimpleName()
                           + ". Multiple fields with the same name exist in the class hierarchy."),
                  filterField,
                  FieldResolutionMode.STRICT
         );
      }

      return new FieldResolutionResult(
               null,
               List.of(),
               List.of("No deterministic field match was found for '" + filterField + "' on entity "
                        + clazz.getSimpleName() + "."),
               filterField,
               FieldResolutionMode.STRICT
      );
   }

   private static FieldResolutionResult resolveFuzzy(Class<?> clazz, String filterField)
   {
      FieldResolutionResult strictResult = resolveStrict(clazz, filterField);
      if (strictResult.resolved() || strictResult.errors().stream().anyMatch(e -> e.startsWith("Ambiguous field")))
      {
         return strictResult;
      }

      List<Field> allFields = getAllFieldsFromHierarchy(clazz);
      if (allFields.isEmpty())
      {
         return new FieldResolutionResult(
                  null,
                  List.of(),
                  List.of("There is no similar field for the filter '" + filterField + "' in " + clazz.getSimpleName()
                           + "."),
                  filterField,
                  FieldResolutionMode.FUZZY
         );
      }

      int bestDistance = allFields.stream()
               .map(Field::getName)
               .mapToInt(name -> levenshteinDistance(name, filterField))
               .min()
               .orElse(Integer.MAX_VALUE);

      List<Field> bestMatches = allFields.stream()
               .filter(f -> levenshteinDistance(f.getName(), filterField) == bestDistance)
               .collect(Collectors.toList());

      if (bestMatches.size() > 1)
      {
         String names = bestMatches.stream().map(Field::getName).distinct().collect(Collectors.joining(", "));
         return new FieldResolutionResult(
                  null,
                  List.of("Filter '" + filterField + "' has ambiguous fuzzy matches: " + names + "."),
                  List.of("Ambiguous fuzzy field match for '" + filterField + "' on entity " + clazz.getSimpleName()
                           + "."),
                  filterField,
                  FieldResolutionMode.FUZZY
         );
      }

      Field field = bestMatches.get(0);
      field.setAccessible(true);
      return new FieldResolutionResult(
               field,
               List.of("Filter '" + filterField + "' not found, using the most similar field: '" + field.getName()
                        + "'."),
               List.of(),
               filterField,
               FieldResolutionMode.FUZZY
      );
   }

   // helper levenshtein
   private static int levenshteinDistance(String s1, String s2)
   {
      int[] costs = new int[s2.length() + 1];
      for (int j = 0; j < costs.length; j++)
         costs[j] = j;
      for (int i = 1; i <= s1.length(); i++)
      {
         costs[0] = i;
         int nw = i - 1;
         for (int j = 1; j <= s2.length(); j++)
         {
            int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                     s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
            nw = costs[j];
            costs[j] = cj;
         }
      }
      return costs[s2.length()];
   }

   private static List<Field> getFieldsFromHierarchy(Class<?> clazz, String fieldName)
   {
      List<Field> fields = new ArrayList<>();
      Class<?> current = clazz;
      while (current != null && current != Object.class)
      {
         try
         {
            fields.add(current.getDeclaredField(fieldName));
         }
         catch (NoSuchFieldException e)
         {
            // continue through the hierarchy
         }
         current = current.getSuperclass();
      }
      return fields;
   }

   private static List<Field> getAllFieldsFromHierarchy(Class<?> clazz)
   {
      List<Field> fields = new ArrayList<>();
      Class<?> current = clazz;
      while (current != null && current != Object.class)
      {
         fields.addAll(Arrays.asList(current.getDeclaredFields()));
         current = current.getSuperclass();
      }
      return fields;
   }

}

package ua.quietlymavenplugin.render.javaparser;

import ua.quietlymavenplugin.render.config.FieldResolutionMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FieldResolutionResult
{

   private final Field field;
   private final List<String> warnings;
   private final List<String> errors;
   private final String attemptedName;
   private final FieldResolutionMode mode;

   public FieldResolutionResult(
            Field field,
            List<String> warnings,
            List<String> errors,
            String attemptedName,
            FieldResolutionMode mode
   )
   {
      this.field = field;
      this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
      this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
      this.attemptedName = attemptedName;
      this.mode = mode;
   }

   public boolean resolved()
   {
      return field != null;
   }

   public Optional<Field> field()
   {
      return Optional.ofNullable(field);
   }

   public List<String> warnings()
   {
      return warnings;
   }

   public List<String> errors()
   {
      return errors;
   }

   public String attemptedName()
   {
      return attemptedName;
   }

   public FieldResolutionMode mode()
   {
      return mode;
   }
}

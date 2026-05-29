package ua.quietlymavenplugin.render.javaparser;

import com.acme.model.AmbiguousCustomer;
import com.acme.model.Customer;
import com.acme.model.FuzzyAmbiguousCustomer;
import org.junit.jupiter.api.Test;
import ua.quietlymavenplugin.render.config.FieldResolutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldResolverTest {

   @Test
   public void strict_resolves_exact_field_match() {
      FieldResolutionResult result = FieldResolver.resolveField(Customer.class, "status", FieldResolutionMode.STRICT);

      assertTrue(result.resolved());
      assertEquals("status", result.field().orElseThrow().getName());
      assertTrue(result.errors().isEmpty());
   }

   @Test
   public void strict_reports_missing_field_without_guessing() {
      FieldResolutionResult result = FieldResolver.resolveField(Customer.class, "statuz", FieldResolutionMode.STRICT);

      assertFalse(result.resolved());
      assertTrue(result.warnings().isEmpty());
      assertTrue(result.errors().get(0).contains("No deterministic field match"));
   }

   @Test
   public void strict_reports_ambiguous_hierarchy_match() {
      FieldResolutionResult result = FieldResolver.resolveField(AmbiguousCustomer.class, "code", FieldResolutionMode.STRICT);

      assertFalse(result.resolved());
      assertTrue(result.errors().get(0).contains("Ambiguous field"));
   }

   @Test
   public void fuzzy_match_is_opt_in() {
      FieldResolutionResult strict = FieldResolver.resolveField(Customer.class, "statuz", FieldResolutionMode.STRICT);
      FieldResolutionResult fuzzy = FieldResolver.resolveField(Customer.class, "statuz", FieldResolutionMode.FUZZY);

      assertFalse(strict.resolved());
      assertTrue(fuzzy.resolved());
      assertEquals("status", fuzzy.field().orElseThrow().getName());
   }

   @Test
   public void fuzzy_collects_warning() {
      FieldResolutionResult result = FieldResolver.resolveField(Customer.class, "statuz", FieldResolutionMode.FUZZY);

      assertTrue(result.resolved());
      assertFalse(result.warnings().isEmpty());
      assertTrue(result.warnings().get(0).contains("using the most similar field"));
   }

   @Test
   public void fuzzy_reports_ambiguous_best_match() {
      FieldResolutionResult result = FieldResolver.resolveField(FuzzyAmbiguousCustomer.class, "statu", FieldResolutionMode.FUZZY);

      assertFalse(result.resolved());
      assertFalse(result.warnings().isEmpty());
      assertTrue(result.errors().get(0).contains("Ambiguous fuzzy field match"));
   }
}

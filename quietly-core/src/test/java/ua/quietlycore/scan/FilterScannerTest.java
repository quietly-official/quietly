package ua.quietlycore.scan;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.junit.jupiter.api.Test;
import ua.quietlycore.model.FilterInfo;
import ua.quietlycore.model.FilterEntityInfo;
import ua.quietlycore.scan.fixtures.allowed.FilteredEntity;
import ua.quietlycore.scan.fixtures.allowed.PlainEntity;
import ua.quietlycore.scan.fixtures.allowed.deep.NestedFilteredEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterScannerTest {

   @Test
   public void scan_entity_keeps_namespaced_prefix_and_uses_last_segment_as_field() {
      List<FilterInfo> filters = FilterScanner.scanEntity(Customer.class);

      assertEquals(1, filters.size());
      assertEquals("customer.obj", filters.get(0).prefix);
      assertEquals("fornitore_uuid", filters.get(0).field);
      assertEquals("fornitore_uuid", filters.get(0).paramName);
      assertEquals(String.class, filters.get(0).paramType);
   }

   @Test
   public void application_scan_includes_all_entities_in_allowed_package() throws Exception {
      List<FilterEntityInfo> entities = scan(
               EntityScanOptions.allApplicationEntities("ua.quietlycore.scan.fixtures.allowed")
      );

      Set<Class<?>> entityClasses = entityClasses(entities);
      assertTrue(entityClasses.contains(FilteredEntity.class));
      assertTrue(entityClasses.contains(PlainEntity.class));
   }

   @Test
   public void filter_scan_excludes_entities_without_filters() throws Exception {
      List<FilterEntityInfo> entities = scan(
               EntityScanOptions.filteredApplicationEntities("ua.quietlycore.scan.fixtures.allowed")
      );

      Set<Class<?>> entityClasses = entityClasses(entities);
      assertTrue(entityClasses.contains(FilteredEntity.class));
      assertFalse(entityClasses.contains(PlainEntity.class));
   }

   @Test
   public void scan_excludes_entities_outside_configured_package() throws Exception {
      List<FilterEntityInfo> entities = scan(
               EntityScanOptions.filteredApplicationEntities("ua.quietlycore.scan.fixtures.allowed")
      );

      assertEquals(Set.of(FilteredEntity.class), entityClasses(entities));
   }

   @Test
   public void package_wildcard_includes_subpackages_but_not_siblings() throws Exception {
      List<FilterEntityInfo> entities = scan(
               EntityScanOptions.filteredApplicationEntities("ua.quietlycore.scan.fixtures.allowed.*")
      );

      assertEquals(Set.of(FilteredEntity.class, NestedFilteredEntity.class), entityClasses(entities));
   }

   private List<FilterEntityInfo> scan(EntityScanOptions options) throws Exception {
      String testClasses = Path.of(
               FilterScannerTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()
      ).toString();
      return FilterScanner.scanProjectEntities(List.of(testClasses), testClasses, options);
   }

   private Set<Class<?>> entityClasses(List<FilterEntityInfo> entities) {
      return entities.stream()
               .map(FilterEntityInfo::entityClass)
               .collect(Collectors.toSet());
   }

   @Entity
   @FilterDef(name = "customer.obj.fornitore_uuid", parameters = @ParamDef(name = "fornitore_uuid", type = String.class))
   @Filter(name = "customer.obj.fornitore_uuid", condition = " fornitore_id = :fornitore_uuid ")
   public static class Customer
   {
   }
}

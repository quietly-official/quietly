package ua.quietlycore.scan;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.junit.jupiter.api.Test;
import ua.quietlycore.model.FilterInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

   @Entity
   @FilterDef(name = "customer.obj.fornitore_uuid", parameters = @ParamDef(name = "fornitore_uuid", type = String.class))
   @Filter(name = "customer.obj.fornitore_uuid", condition = " fornitore_id = :fornitore_uuid ")
   public static class Customer
   {
   }
}

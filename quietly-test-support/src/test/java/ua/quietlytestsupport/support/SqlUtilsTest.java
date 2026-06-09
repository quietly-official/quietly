package ua.quietlytestsupport.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlUtilsTest
{

   @Test
   public void loads_existing_fixture_from_classpath() throws Exception
   {
      String sql = SqlUtils.loadSqlFromClasspath("/sql/customer.sql");

      assertTrue(sql.contains("insert into customer"));
   }

   @Test
   public void missing_fixture_fails_with_explicit_path()
   {
      IllegalStateException exception = assertThrows(
               IllegalStateException.class,
               () -> SqlUtils.loadSqlFromClasspath("sql/missing.sql")
      );

      assertTrue(exception.getMessage().contains("sql/missing.sql"));
   }
}

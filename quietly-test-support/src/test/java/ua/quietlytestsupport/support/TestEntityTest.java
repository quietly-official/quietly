package ua.quietlytestsupport.support;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestEntityTest
{

   @Test
   @SuppressWarnings("unchecked")
   public void returns_null_when_no_test_entity_exists()
   {
      AtomicReference<String> executedQuery = new AtomicReference<>();
      TypedQuery<TestEntity> query = (TypedQuery<TestEntity>) Proxy.newProxyInstance(
               getClass().getClassLoader(),
               new Class<?>[]{TypedQuery.class},
               (proxy, method, args) -> switch (method.getName())
               {
               case "setMaxResults" -> proxy;
               case "getResultList" -> List.of();
               default -> defaultValue(method.getReturnType());
               }
      );
      EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
               getClass().getClassLoader(),
               new Class<?>[]{EntityManager.class},
               (proxy, method, args) -> {
                  if ("createQuery".equals(method.getName()))
                  {
                     executedQuery.set((String) args[0]);
                     return query;
                  }
                  return defaultValue(method.getReturnType());
               }
      );

      TestSupport support = new TestSupport();

      assertNull(support.test_entity(entityManager, TestEntity.class, null));
      assertEquals("SELECT e FROM TestEntity e", executedQuery.get());
   }

   private static Object defaultValue(Class<?> returnType)
   {
      if (!returnType.isPrimitive())
      {
         return null;
      }
      if (returnType == boolean.class)
      {
         return false;
      }
      if (returnType == char.class)
      {
         return '\0';
      }
      return 0;
   }

   public static class TestEntity extends PanacheEntityBase
   {
   }

   static class TestSupport extends ServiceRsTestUtilsV1
   {
   }
}

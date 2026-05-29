package ua.quietlytestsupport.support;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.assertj.core.api.SoftAssertions;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ServiceRsTestUtilsV1 {

   public static String loadSqlFromClasspath(String filePath) throws IOException {
      return SqlUtils.loadSqlFromClasspath(filePath);
   }

   /**
    * CORE: Esegue il test risolvendo il campo una sola volta
    */
   protected <T> void execute_filter_logic(
            String query_param,
            Object query_param_value,
            Class<T> map_into_class,
            String filter_type_name,
            BiConsumer<Object, SoftAssertions> validation_logic
   ) {

      String fieldName = query_param.contains(".")
               ? query_param.substring(query_param.lastIndexOf('.') + 1)
               : query_param;

      final Field finalField = getFieldFromHierarchy(map_into_class, fieldName);
      if (finalField == null) {
         throw new RuntimeException("Il campo " + fieldName + " non esiste in " + map_into_class.getSimpleName());
      }
      finalField.setAccessible(true);

      Response response = RestAssured.given()
               .queryParam(query_param, formatQueryValue(query_param_value))
               .when().get()
               .then().extract().response();

      assertThat(response.statusCode())
               .as("Il filtro " + query_param + " ha causato un errore 500.")
               .isEqualTo(200);

      List<T> results = get_response_body_as_list_of(response, map_into_class);
      assertThat(results).as("La risposta per il filtro " + filter_type_name).isNotEmpty();

      SoftAssertions softly = new SoftAssertions();

      results.forEach(entity -> {
         try {
            Object rawValue = finalField.get(entity);
            validation_logic.accept(rawValue, softly);
         } catch (Exception e) {
            softly.fail("Errore lettura campo " + fieldName);
         }
      });

      softly.assertAll();
   }

   /**
    * Cerca il campo nella classe e in tutte le sue superclassi (necessario per Panache)
    */
   private Field getFieldFromHierarchy(Class<?> clazz, String fieldName) {
      Class<?> current = clazz;
      while (current != null && current != Object.class) {
         try {
            return current.getDeclaredField(fieldName);
         } catch (NoSuchFieldException e) {
            current = current.getSuperclass();
         }
      }
      return null;
   }

   /**
    * Parsing della Response nella classe passata
    */
   protected <T> List<T> get_response_body_as_list_of(Response response, Class<T> clazz) {
      Class<T[]> arrayType = (Class<T[]>) Array.newInstance(clazz, 0).getClass();
      return Arrays.asList(response.getBody().as(arrayType));
   }

   /**
    * Implementazione dei filtri obj
    */
   protected <T> void obj_filter_test(String param, Object val, Class<T> clazz) {
      execute_filter_logic(param, val, clazz, "OBJ", (actual, softly) -> {

         softly.assertThat(actual)
                  .as("Il campo '" + param + "' è null")
                  .isNotNull();

         if (actual == null || val == null) return;

         if (actual instanceof Enum<?> e) {
            softly.assertThat(e.name())
                     .as("Valore enum non corrispondente")
                     .isEqualTo(val.toString());
         } else {
            softly.assertThat(actual)
                     .as("Valore non corrispondente per " + param)
                     .isEqualTo(val);
         }
      });
   }

   /**
    * Implementazione dei filtri like
    */
   protected <T> void like_filter_test(String param, Object val, Class<T> clazz) {

      String cleanVal = val == null ? "" : val.toString().replace("%", "").toLowerCase();

      execute_filter_logic(param, val, clazz, "LIKE", (actual, softly) -> {
         softly.assertThat(actual).isNotNull();
         softly.assertThat(actual.toString().toLowerCase()).contains(cleanVal);
      });
   }

   /**
    * Implementazione dei filtri from
    */
   protected <T> void from_filter_test(String param, Object val, Class<T> clazz) {
      execute_filter_logic(param, val, clazz, "FROM", (actual, softly) -> {
         compare_dates(actual, val, true, softly);
      });
   }

   /**
    * Implementazione dei filtri to
    */
   protected <T> void to_filter_test(String param, Object val, Class<T> clazz) {
      execute_filter_logic(param, val, clazz, "TO", (actual, softly) -> {
         compare_dates(actual, val, false, softly);
      });
   }

   /**
    * Implementazione dei filtri nil
    */
   protected <T> void nil_filter_test(String param, Class<T> clazz) {
      execute_filter_logic(param, null, clazz, "NIL", (actual, softly) -> {
         softly.assertThat(actual).isNull();
      });
   }

   /**
    * Implementazione dei filtri not_nil
    */
   protected <T> void not_nil_filter_test(String param, Class<T> clazz) {
      execute_filter_logic(param, null, clazz, "NOT_NIL", (actual, softly) -> {
         softly.assertThat(actual).isNotNull();
      });
   }

   /**
    * Comparatore di date
    */
   private void compare_dates(Object actual, Object expected, boolean isFrom, SoftAssertions softly) {

      if (actual == null || expected == null) return;

      if (actual instanceof LocalDate a && expected instanceof LocalDate e) {
         if (isFrom) softly.assertThat(a).isAfterOrEqualTo(e);
         else softly.assertThat(a).isBeforeOrEqualTo(e);
      }

      else if (actual instanceof LocalDateTime a && expected instanceof LocalDateTime e) {
         if (isFrom) softly.assertThat(a).isAfterOrEqualTo(e);
         else softly.assertThat(a).isBeforeOrEqualTo(e);
      }

      else if (actual instanceof ZonedDateTime a && expected instanceof ZonedDateTime e) {
         if (isFrom) softly.assertThat(a).isAfterOrEqualTo(e);
         else softly.assertThat(a).isBeforeOrEqualTo(e);
      }
   }

   /**
    * PanacheQuery per estrarre value da utilizzare nei test
    */
   public <T extends PanacheEntityBase> T test_entity(EntityManager em, Class<T> clazz, String query,
            Object... params) {
      try {
         // query
         String jpql = (query == null || query.isBlank())
                  ? "SELECT e FROM " + clazz.getSimpleName() + " e"
                  : "SELECT e FROM " + clazz.getSimpleName() + " e WHERE " + query;

         TypedQuery<T> typedQuery = em.createQuery(jpql, clazz);

         // mapping dei param
         if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
               typedQuery.setParameter(i + 1, params[i]);
            }
         }

         // recupera il primo per permettere la sintassi del campo
         List<T> results = typedQuery.setMaxResults(1).getResultList();
         return results.isEmpty() ? null : results.get(0);

      } catch (Exception e) {
         throw new RuntimeException("Errore in test_entity() su " + clazz.getSimpleName(), e);
      }
   }

   /**
    * Metodo helper per convertire la stringa del test nel tipo reale del campo
    */
   private Object convertValueToFieldType(String value, Class<?> type) {
      if (value == null || value.isEmpty())
         return value;

      if (type.equals(Integer.class) || type.equals(int.class)) {
         return Integer.parseInt(value);
      }
      if (type.equals(Long.class) || type.equals(long.class)) {
         return Long.parseLong(value);
      }
      if (type.equals(Boolean.class) || type.equals(boolean.class)) {
         return Boolean.parseBoolean(value);
      }
      if (type.isEnum()) {
         // se è un enum, RestAssured deve mandare la stringa,
         // ma è utile validare qui se il valore è corretto
         return value;
      }
      return value;
   }

   private Object formatQueryValue(Object value) {

      if (value instanceof LocalDateTime ldt) {
         return ldt.toString(); // ISO-8601
      }

      if (value instanceof LocalDate ld) {
         return ld.toString();
      }

      if (value instanceof ZonedDateTime zdt) {
         return zdt.toLocalDateTime().toString();
      }

      return value;
   }
}

package ua.quietlytestsupport.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FilterTestBaseTest
{

   private static HttpServer server;
   private static TestFilterSupport support;

   @BeforeAll
   public static void startServer() throws IOException
   {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/", FilterTestBaseTest::handleRequest);
      server.start();

      RestAssured.baseURI = "http://localhost";
      RestAssured.port = server.getAddress().getPort();
      RestAssured.basePath = "";
      support = new TestFilterSupport();
   }

   @AfterAll
   public static void stopServer()
   {
      if (server != null)
      {
         server.stop(0);
      }
      RestAssured.reset();
   }

   private static void handleRequest(HttpExchange exchange) throws IOException
   {
      Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
      support.recordQuery(query);

      String body;
      if (query.containsKey("obj.status"))
      {
         body = "[{\"status\":\"ACTIVE\"}]";
      }
      else if (query.containsKey("like.name"))
      {
         body = "[{\"name\":\"John Doe\"}]";
      }
      else if (query.containsKey("from.createdOn"))
      {
         body = "[{\"createdOn\":\"2024-01-02\"}]";
      }
      else if (query.containsKey("to.createdOn"))
      {
         body = "[{\"createdOn\":\"2024-01-30\"}]";
      }
      else if (query.containsKey("nil.deletedAt"))
      {
         body = "[{\"deletedAt\":null}]";
      }
      else if (query.containsKey("not_nil.deletedAt"))
      {
         body = "[{\"deletedAt\":\"2024-01-15T10:30:00\"}]";
      }
      else if (query.containsKey("obj.createdOn"))
      {
         body = "[{\"createdOn\":\"2024-01-15\"}]";
      }
      else if (query.containsKey("obj.state"))
      {
         body = "[{\"state\":\"ACTIVE\"}]";
      }
      else
      {
         body = "[]";
      }

      byte[] response = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
   }

   private static Map<String, String> parseQuery(String rawQuery)
   {
      Map<String, String> values = new LinkedHashMap<>();
      if (rawQuery == null || rawQuery.isBlank())
      {
         return values;
      }
      for (String pair : rawQuery.split("&"))
      {
         String[] parts = pair.split("=", 2);
         String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
         String value = parts.length == 2
                  ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                  : "";
         values.put(key, value);
      }
      return values;
   }

   @Test
   public void obj_filter_validates_exact_value()
   {
      support.verify("obj.status", "ACTIVE");
   }

   @Test
   public void like_filter_validates_contained_value()
   {
      support.verify("like.name", "%john%");
   }

   @Test
   public void from_filter_validates_lower_date_boundary()
   {
      support.verify("from.createdOn", LocalDate.of(2024, 1, 1));
   }

   @Test
   public void from_filter_rejects_value_before_lower_date_boundary()
   {
      assertThrows(
               AssertionError.class,
               () -> support.verify("from.createdOn", LocalDate.of(2024, 2, 1))
      );
   }

   @Test
   public void to_filter_validates_upper_date_boundary()
   {
      support.verify("to.createdOn", LocalDate.of(2024, 1, 31));
   }

   @Test
   public void to_filter_rejects_value_after_upper_date_boundary()
   {
      assertThrows(
               AssertionError.class,
               () -> support.verify("to.createdOn", LocalDate.of(2024, 1, 1))
      );
   }

   @Test
   public void nil_filter_validates_null_value()
   {
      support.verify("nil.deletedAt", null);
   }

   @Test
   public void not_nil_filter_validates_non_null_value()
   {
      support.verify("not_nil.deletedAt", null);
   }

   @Test
   public void local_date_value_is_sent_as_iso_date()
   {
      support.verify("obj.createdOn", LocalDate.of(2024, 1, 15));
      assertEquals("2024-01-15", support.lastQueryValue("obj.createdOn"));
   }

   @Test
   public void enum_filter_compares_enum_name()
   {
      support.verify("obj.state", "ACTIVE");
   }

   public enum TestState
   {
      ACTIVE,
      INACTIVE
   }

   public static class FilterDto
   {
      public String status;
      public String name;
      public LocalDate createdOn;
      public LocalDateTime deletedAt;
      public TestState state;
   }

   public static class TestFilterSupport extends FilterTestBase
   {

      private Map<String, String> lastQuery = Map.of();

      void verify(String path, Object value)
      {
         assert_filter_works(path, value, FilterDto.class);
      }

      void recordQuery(Map<String, String> query)
      {
         lastQuery = Map.copyOf(query);
      }

      String lastQueryValue(String name)
      {
         return lastQuery.get(name);
      }
   }
}

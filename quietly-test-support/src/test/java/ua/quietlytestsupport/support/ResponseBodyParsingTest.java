package ua.quietlytestsupport.support;

import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponseBodyParsingTest
{

   private final TestSupport support = new TestSupport();

   @Test
   public void parses_direct_json_array()
   {
      List<CustomerDto> customers = support.parse(
               response("[{\"name\":\"Ada\"}]"),
               CustomerDto.class
      );

      assertEquals(List.of("Ada"), customers.stream().map(customer -> customer.name).toList());
   }

   @Test
   public void parses_items_wrapper()
   {
      List<CustomerDto> customers = support.parse(
               response("{\"items\":[{\"name\":\"Ada\"}]}"),
               CustomerDto.class
      );

      assertEquals(List.of("Ada"), customers.stream().map(customer -> customer.name).toList());
   }

   @Test
   public void parses_content_wrapper()
   {
      List<CustomerDto> customers = support.parse(
               response("{\"content\":[{\"name\":\"Ada\"}],\"total\":1}"),
               CustomerDto.class
      );

      assertEquals(List.of("Ada"), customers.stream().map(customer -> customer.name).toList());
   }

   private Response response(String body)
   {
      return new ResponseBuilder()
               .setStatusCode(200)
               .setContentType("application/json")
               .setBody(body)
               .build();
   }

   public static class CustomerDto
   {
      public String name;
   }

   public static class TestSupport extends ServiceRsTestUtilsV1
   {
      <T> List<T> parse(Response response, Class<T> type)
      {
         return get_response_body_as_list_of(response, type);
      }
   }
}

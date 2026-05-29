package ua.quietlymavenplugin.render.config;

import com.acme.model.Customer;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuietlyPluginConfigTest {

   @Test
   public void defaults_preserve_current_conventions() {
      MavenProject project = new MavenProject();
      project.setFile(new File("pom.xml").getAbsoluteFile());

      QuietlyPluginConfig config = QuietlyPluginConfig.defaults(project);

      assertEquals("com.acme", config.resolveRootPackage(Customer.class));
      assertEquals("com.acme.services.rs", config.resolveServicePackage(Customer.class));
      assertEquals("CustomerServiceRs", config.resolveServiceName(Customer.class));
      assertEquals("com.acme.services.rs.CustomerServiceRs", config.resolveServiceClassName(Customer.class));
      assertFalse(config.disabledByDefault());
      assertTrue(config.failOnMissingService());
      assertTrue(config.failOnUnresolvedField());
      assertEquals(FieldResolutionMode.STRICT, config.fieldResolutionMode());
   }

   @Test
   public void custom_patterns_resolve_placeholders() {
      MavenProject project = new MavenProject();
      project.setFile(new File("pom.xml").getAbsoluteFile());

      QuietlyPluginConfig config = new QuietlyPluginConfig(
               project,
               "com.acme",
               "${basePackage}.domain",
               "${basePackage}.api",
               "${entitySimpleName}Resource",
               new File("target/generated-test-sources/quietly"),
               new File("target/quietly/filters-report.md"),
               true,
               false,
               false,
               true,
               FieldResolutionMode.FUZZY
      );

      assertEquals("com.acme.api", config.resolveServicePackage(Customer.class));
      assertEquals("CustomerResource", config.resolveServiceName(Customer.class));
      assertEquals("com.acme.api.CustomerResource", config.resolveServiceClassName(Customer.class));
      assertTrue(config.disabledByDefault());
      assertFalse(config.failOnMissingService());
      assertFalse(config.failOnUnresolvedField());
      assertTrue(config.dryRun());
      assertEquals(FieldResolutionMode.FUZZY, config.fieldResolutionMode());
   }
}

package ua.quietlymavenplugin.render.config;

import java.util.List;

public class TestImportsConstants {

   public static final List<String> CORE_TEST_IMPORTS = List.of(
            "org.junit.jupiter.api.Test",
            "io.quarkus.test.security.TestSecurity",
            "io.quarkus.test.junit.QuarkusTest",
            "jakarta.inject.Inject",
            "jakarta.persistence.EntityManager",
            "jakarta.transaction.Transactional",
            "ua.quietlytestsupport.support.FilterTestBase",
            "io.quarkus.test.common.http.TestHTTPEndpoint",
            "org.junit.jupiter.api.BeforeEach",
            "ua.quietlytestsupport.support.SqlUtils",
            "java.io.IOException",
            "org.junit.jupiter.api.Assertions"
   );

}

package ua.quietlymavenplugin.discovery;

import java.nio.file.Path;
import java.util.Optional;

public record FixtureResolution(
         Optional<String> tableName,
         Optional<Path> expectedSqlPath,
         boolean exists,
         Optional<String> failureReason
)
{

   public FixtureResolution
   {
      tableName = tableName == null ? Optional.empty() : tableName;
      expectedSqlPath = expectedSqlPath == null ? Optional.empty() : expectedSqlPath;
      failureReason = failureReason == null ? Optional.empty() : failureReason;
   }
}

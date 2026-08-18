package ua.quietlymavenplugin.discovery;

import java.util.Optional;

public record ServiceResolution(
         String expectedClassName,
         String servicePackage,
         String serviceSimpleName,
         boolean exists,
         Optional<Class<?>> resolvedClass,
         Optional<String> failureReason
)
{

   public ServiceResolution
   {
      resolvedClass = resolvedClass == null ? Optional.empty() : resolvedClass;
      failureReason = failureReason == null ? Optional.empty() : failureReason;
   }
}

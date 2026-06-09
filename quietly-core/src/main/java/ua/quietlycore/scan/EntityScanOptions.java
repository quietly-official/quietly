package ua.quietlycore.scan;

public record EntityScanOptions(
         String entityPackagePattern,
         boolean requireHibernateFilters
)
{

   public static EntityScanOptions allApplicationEntities(String entityPackagePattern)
   {
      return new EntityScanOptions(entityPackagePattern, false);
   }

   public static EntityScanOptions filteredApplicationEntities(String entityPackagePattern)
   {
      return new EntityScanOptions(entityPackagePattern, true);
   }

   public boolean matchesPackage(Class<?> entityClass)
   {
      if (entityPackagePattern == null || entityPackagePattern.isBlank())
      {
         return true;
      }

      String packageName = entityClass.getPackageName();
      String pattern = entityPackagePattern.trim();
      if (pattern.endsWith(".*"))
      {
         String packagePrefix = pattern.substring(0, pattern.length() - 2);
         return packageName.equals(packagePrefix) || packageName.startsWith(packagePrefix + ".");
      }
      return packageName.equals(pattern);
   }
}

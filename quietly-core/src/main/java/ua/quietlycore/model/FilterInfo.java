package ua.quietlycore.model;

public class FilterInfo
{
   public String prefix;    // "obj"
   public String field;     // "uuids"
   public String condition; // "uuid IN :uuids
   public String paramName; // hibernate param
   public Class<?> paramType; // String ecc...

   @Override
   public String toString()
   {
      return "FilterInfo{" +
               "prefix='" + prefix + '\'' +
               ", field='" + field + '\'' +
               ", paramName='" + paramName + '\'' +
               ", paramType='" + paramType + '\'' +
               '}';
   }
}

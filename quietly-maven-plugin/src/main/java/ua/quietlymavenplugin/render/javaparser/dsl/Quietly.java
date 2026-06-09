package ua.quietlymavenplugin.render.javaparser.dsl;

public class Quietly
{

   public static MethodDsl method(String returnType, String name)
   {
      return new MethodDsl(returnType, name);
   }

   public static StatementDsl statements()
   {
      return new StatementDsl();
   }

   public static ExpressionDsl dsl()
   {
      return new ExpressionDsl();
   }

}


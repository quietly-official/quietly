package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.Collection;

/**
 * Conversione + gestione import + nomi
 */
public class Expressions
{

   /**
    * Converte dinamicamente qualunque oggetto Java comune in un Expression JavaParser.
    */
   public static Expression toExpression(Object value)
   {

      if (value == null)
      {
         return new NullLiteralExpr();
      }

      // già Expression -> ritorna
      if (value instanceof Expression e)
      {
         return e;
      }

      // String
      if (value instanceof String s)
      {
         return new StringLiteralExpr(s);
      }

      // numeri
      if (value instanceof Integer i)
      {
         return new IntegerLiteralExpr(String.valueOf(i));
      }
      if (value instanceof Long l)
      {
         return new LongLiteralExpr(l + "L");
      }
      if (value instanceof Double d)
      {
         return new DoubleLiteralExpr(String.valueOf(d));
      }
      if (value instanceof Float f)
      {
         return new DoubleLiteralExpr(f + "f");
      }

      // Boolean
      if (value instanceof Boolean b)
      {
         return new BooleanLiteralExpr(b);
      }

      // class literal -> SomeClass.class
      if (value instanceof Class<?> clazz)
      {
         return new ClassExpr(new ClassOrInterfaceType(null, clazz.getSimpleName()));
      }

      // enum -> EnumType.VALUE
      if (value instanceof Enum<?> en)
      {
         return new FieldAccessExpr(
                  new NameExpr(en.getClass().getSimpleName()),
                  en.name()
         );
      }

      // collection -> { a, b, c }
      if (value instanceof Collection<?> col)
      {
         NodeList<Expression> values = new NodeList<>();
         col.forEach(v -> values.add(toExpression(v)));
         return new ArrayInitializerExpr(values);
      }

      throw new IllegalArgumentException("Type " + value.getClass() + " not supported in toExpression().");
   }

   /**
    * Crea NameExpr dinamico (per variabili o costanti)
    */
   public static NameExpr name(String name)
   {
      return new NameExpr(name);
   }

}

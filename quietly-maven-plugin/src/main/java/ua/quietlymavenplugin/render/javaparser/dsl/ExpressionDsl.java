package ua.quietlymavenplugin.render.javaparser.dsl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import ua.quietlymavenplugin.render.javaparser.Expressions;

/**
 * Costruisce AST, costruisce espressioni
 */
public class ExpressionDsl
{

   private Expression current;

   // ============================
   // ============ FIELD =========
   // ============================

   public ExpressionDsl field(String name)
   {
      if (current == null)
         throw new IllegalStateException("field() called before initializing expression");
      current = new FieldAccessExpr(current, name);
      return this;
   }

   public Expression build()
   {
      return current;
   }

   // ============================
   // ============ CALL ==========
   // ============================

   public ExpressionDsl call(String methodName)
   {
      current = new MethodCallExpr(null, methodName);
      return this;
   }

   public ExpressionDsl staticCall(Type clazz, String methodName)
   {
      current = new MethodCallExpr(new NameExpr(clazz.asString()), methodName);
      return this;
   }

   public ExpressionDsl callMethod(String name)
   {
      current = new MethodCallExpr(current, name);
      return this;
   }

   public ExpressionDsl maybeCall(boolean condition, String name)
   {
      if (condition)
         callMethod(name);
      return this;
   }

   public MethodCallExpr buildCall()
   {
      if (!(current instanceof MethodCallExpr))
         throw new IllegalStateException("Current expression is not a MethodCallExpr");
      return (MethodCallExpr) current;
   }

   // ============================
   // ========== ARGUMENTS =======
   // ============================

   public ExpressionDsl argument(Expression expr)
   {
      if (!(current instanceof MethodCallExpr call))
         throw new IllegalStateException("argument() called on a non-MethodCallExpr");
      call.addArgument(expr);
      return this;
   }

   // primitive, literal arguments
   public ExpressionDsl argumentInt(int value)
   {
      return argument(new IntegerLiteralExpr(String.valueOf(value)));
   }

   public ExpressionDsl argumentLong(long value)
   {
      return argument(new LongLiteralExpr(String.valueOf(value) + "L"));
   }

   public ExpressionDsl argumentDouble(double value)
   {
      return argument(new DoubleLiteralExpr(String.valueOf(value)));
   }

   public ExpressionDsl argumentBoolean(boolean value)
   {
      return argument(new BooleanLiteralExpr(value));
   }

   public ExpressionDsl argumentString(String value)
   {
      return argument(new StringLiteralExpr(value));
   }

   public ExpressionDsl argumentNull()
   {
      return argument(new NullLiteralExpr());
   }

   // non-primitive object arguments
   public ExpressionDsl argumentClass(Type type)
   {
      return argument(new ClassExpr(type));
   }

   public ExpressionDsl argumentName(String name)
   {
      return argument(new NameExpr(name));
   }

   // concatenation helper
   public ExpressionDsl argumentConcatenation(Object... parts)
   {
      Expression expr = null;
      for (Object part : parts)
      {
         Expression e;
         if (part instanceof ExpressionDsl dsl)
         {
            e = dsl.build();
         }
         else
         {
            e = Expressions.toExpression(part);
         }
         expr = (expr == null) ? e : new BinaryExpr(expr, e, BinaryExpr.Operator.PLUS);
      }
      return argument(expr);
   }

   // optional argument
   public ExpressionDsl optionalArgument(boolean condition, Expression expr)
   {
      if (condition)
         argument(expr);
      return this;
   }

   public ExpressionDsl classLiteral(String className)
   {
      this.current = new ClassExpr(StaticJavaParser.parseClassOrInterfaceType(className));
      return this;
   }
}
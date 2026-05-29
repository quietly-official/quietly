package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;


public class ExpressionHelpers {

   public static Expression concat(Object... parts) {
      Expression expr = null;
      for (Object p : parts) {
         Expression e = Expressions.toExpression(p);
         expr = (expr == null) ? e : new BinaryExpr(expr, e, BinaryExpr.Operator.PLUS);
      }
      return expr;
   }

   public static AssignExpr assign(String var, Expression value) {
      return new AssignExpr(new NameExpr(var), value, AssignExpr.Operator.ASSIGN);
   }

}

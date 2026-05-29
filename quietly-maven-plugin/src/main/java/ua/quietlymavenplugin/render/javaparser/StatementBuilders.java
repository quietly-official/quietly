package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;

/**
 *
 */
public class StatementBuilders {

   public static ExpressionStmt statement(Expression e) {
      return new ExpressionStmt(e);
   }

   public static IfStmt if_statement(Expression condition, Statement thenStmt) {
      return new IfStmt(condition, thenStmt, null);
   }

   public static ReturnStmt return_statement(Expression e) {
      return new ReturnStmt(e);
   }

   public static BlockStmt block_of_statements(Statement... statements) {
      BlockStmt b = new BlockStmt();
      for (Statement s : statements) b.addStatement(s);
      return b;
   }

}

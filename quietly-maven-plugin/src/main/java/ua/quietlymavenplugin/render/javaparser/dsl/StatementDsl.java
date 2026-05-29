package ua.quietlymavenplugin.render.javaparser.dsl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;

public class StatementDsl {

   private final BlockStmt block = new BlockStmt();

   public BlockStmt build() {
      return block;
   }

   // ===============================
   // ====== EXPRESSION STATEMENT ===
   // ===============================

   public StatementDsl stmt(Expression expr) {
      block.addStatement(new ExpressionStmt(expr));
      return this;
   }

   // ===============================
   // ========= VARIABLE DECL =======
   // ===============================

   public StatementDsl var(String type, String name, Expression initializer) {

      VariableDeclarator vd = new VariableDeclarator(
               StaticJavaParser.parseType(type),
               name,
               initializer
      );

      VariableDeclarationExpr expr = new VariableDeclarationExpr(vd);
      block.addStatement(new ExpressionStmt(expr));

      return this;
   }

   // ===============================
   // =========== RETURN ============
   // ===============================

   public StatementDsl returning(Expression expr) {
      block.addStatement(new ReturnStmt(expr));
      return this;
   }

   // ===============================
   // ============= IF ==============
   // ===============================

   public StatementDsl ifStmt(Expression condition, StatementDsl thenBlock) {
      block.addStatement(new IfStmt(condition, thenBlock.build(), null));
      return this;
   }

   public StatementDsl ifElse(Expression condition, StatementDsl thenBlock, StatementDsl elseBlock) {
      block.addStatement(new IfStmt(condition, thenBlock.build(), elseBlock.build()));
      return this;
   }

   // ===============================
   // ============ TRY/CATCH ========
   // ===============================

   public StatementDsl tryCatch(
            StatementDsl tryBlock,
            String exceptionType,
            String exceptionName,
            StatementDsl catchBlock
   ) {
      TryStmt tryStmt = new TryStmt();
      tryStmt.setTryBlock(tryBlock.build());

      CatchClause cc = new CatchClause();
      cc.setParameter(new com.github.javaparser.ast.body.Parameter(
               StaticJavaParser.parseType(exceptionType),
               exceptionName
      ));
      cc.setBody(catchBlock.build());

      tryStmt.getCatchClauses().add(cc);
      block.addStatement(tryStmt);

      return this;
   }

   // ===============================
   // ============== FOR ============
   // ===============================

   public StatementDsl forEach(String varType, String varName, Expression iterable, StatementDsl bodyDsl) {

      VariableDeclarator vd = new VariableDeclarator(
               StaticJavaParser.parseType(varType),
               varName
      );

      ForEachStmt stmt = new ForEachStmt();
      stmt.setVariable(new VariableDeclarationExpr(vd));
      stmt.setIterable(iterable);
      stmt.setBody(bodyDsl.build());

      block.addStatement(stmt);

      return this;
   }
}
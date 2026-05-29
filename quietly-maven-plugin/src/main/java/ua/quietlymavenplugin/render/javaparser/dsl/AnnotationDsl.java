package ua.quietlymavenplugin.render.javaparser.dsl;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import ua.quietlymavenplugin.render.javaparser.Expressions;

/**
 * Costruisce annotazioni
 */
public class AnnotationDsl {

   private final MethodDeclaration method;
   private final MethodDsl parent;
   private AnnotationExpr current;

   public AnnotationDsl(MethodDeclaration method, MethodDsl parent) {
      this.method = method;
      this.parent = parent;
   }

   public AnnotationDsl marker(String name) {
      MarkerAnnotationExpr ann = new MarkerAnnotationExpr(name);
      method.addAnnotation(ann);
      current = ann;
      return this;
   }

   public AnnotationDsl normal(String name) {
      NormalAnnotationExpr ann = new NormalAnnotationExpr();
      ann.setName(name);
      current = ann;
      method.addAnnotation(ann);
      return this;
   }

   public AnnotationDsl param(String key, Object value) {
      if (!(current instanceof NormalAnnotationExpr ann))
         throw new IllegalStateException("param() called without normal()");
      ann.addPair(key, Expressions.toExpression(value));
      return this;
   }

   public AnnotationDsl singleMember(Expression value) {
      if (current instanceof NormalAnnotationExpr ann) {
         // trasforma NormalAnnotationExpr in SingleMemberAnnotationExpr
         SingleMemberAnnotationExpr sma = new SingleMemberAnnotationExpr(
                  ann.getName(), value
         );
         // rimuove la normal precedente
         method.getAnnotations().remove(ann);
         method.addAnnotation(sma);
         current = sma;
         return this;
      } else if (current == null) {
         // crea da zero
         SingleMemberAnnotationExpr sma = new SingleMemberAnnotationExpr(
                  new Name("Unknown"), value
         );
         method.addAnnotation(sma);
         current = sma;
         return this;
      } else {
         throw new IllegalStateException("singleMember() deve essere chiamato su annotation appena creata");
      }
   }

   public AnnotationDsl singleMemberDynamic(String annotationName, Expression valueExpression) {
      SingleMemberAnnotationExpr ann = new SingleMemberAnnotationExpr(
               new Name(annotationName),
               valueExpression
      );
      method.addAnnotation(ann);
      current = ann;
      return this;
   }

   public MethodDsl apply() {
      return parent;
   }
}
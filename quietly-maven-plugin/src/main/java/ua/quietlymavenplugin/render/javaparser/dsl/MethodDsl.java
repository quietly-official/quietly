package ua.quietlymavenplugin.render.javaparser.dsl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;

public class MethodDsl {

   private final MethodDeclaration method = new MethodDeclaration();

   public MethodDsl(String returnType, String name) {
      method.setType(returnType);
      method.setName(name);
   }

   public MethodDeclaration build() {
      return method;
   }

   // =========================
   // ===== MODIFIERS =========
   // =========================

   public MethodDsl public_() {
      method.addModifier(Modifier.Keyword.PUBLIC);
      return this;
   }

   public MethodDsl private_() {
      method.addModifier(Modifier.Keyword.PRIVATE);
      return this;
   }

   public MethodDsl protected_() {
      method.addModifier(Modifier.Keyword.PROTECTED);
      return this;
   }

   public MethodDsl static_() {
      method.addModifier(Modifier.Keyword.STATIC);
      return this;
   }

   // =========================
   // ===== PARAMETERS ========
   // =========================

   public MethodDsl param(String type, String name) {
      Type t = StaticJavaParser.parseType(type);
      method.addParameter(new Parameter(t, name));
      return this;
   }

   // =========================
   // ===== THROWS ============
   // =========================

   public MethodDsl throws_(String exceptionType) {
      method.addThrownException(StaticJavaParser.parseClassOrInterfaceType(exceptionType));
      return this;
   }

   // =========================
   // ===== ANNOTATIONS =======
   // =========================
   public AnnotationDsl annotation(String name) {
      return new AnnotationDsl(method, this).normal(name);
   }

   public MethodDsl markerAnnotation(String name) {
      method.addMarkerAnnotation(name);
      return this;
   }

   // =========================
   // ===== BODY ==============
   // =========================

   public MethodDsl body(StatementDsl bodyDsl) {
      method.setBody(bodyDsl.build());
      return this;
   }


}

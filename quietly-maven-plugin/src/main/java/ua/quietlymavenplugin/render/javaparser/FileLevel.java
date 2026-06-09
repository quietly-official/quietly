package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

public class FileLevel
{

   public static void ensure_package(CompilationUnit cu, String pkg)
   {
      if (cu.getPackageDeclaration().isEmpty())
      {
         cu.setPackageDeclaration(pkg);
      }
   }

   public static MethodDeclaration ensure_method(
            ClassOrInterfaceDeclaration clazz,
            String name
   )
   {
      return clazz.getMethodsByName(name).stream()
               .findFirst()
               .orElseGet(() -> clazz.addMethod(name, Modifier.Keyword.PUBLIC));
   }

   public static ClassOrInterfaceDeclaration ensure_class(
            CompilationUnit cu,
            String name,
            Modifier.Keyword... modifiers
   )
   {
      return cu.getClassByName(name)
               .orElseGet(() -> {
                  var c = cu.addClass(name);
                  for (var m : modifiers)
                     c.addModifier(m);
                  return c;
               });
   }

}

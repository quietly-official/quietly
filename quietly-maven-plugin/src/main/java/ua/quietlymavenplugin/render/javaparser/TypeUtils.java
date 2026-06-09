package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.List;

public class TypeUtils
{

   public static ClassOrInterfaceType type_of(Class<?> clazz, CompilationUnit cu)
   {
      cu.addImport(clazz);
      return new ClassOrInterfaceType(null, clazz.getSimpleName());
   }

   public static ClassOrInterfaceType list_of(Type inner, CompilationUnit cu)
   {
      cu.addImport(List.class);
      return new ClassOrInterfaceType(null, "List")
               .setTypeArguments(inner);
   }

}

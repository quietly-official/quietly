package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestisce import in modo centralizzato, evitando duplicati e gestendo import static
 */
public class ImportManager
{

   private final CompilationUnit cu;
   private final Set<String> normalImports = new HashSet<>();
   private final Set<String> staticImports = new HashSet<>();

   public ImportManager(CompilationUnit cu)
   {
      this.cu = cu;
   }

   /**
    * Aggiunge import evitando duplicati.
    */
   public static void add_imports(List<String> importsToAdd, CompilationUnit cu)
   {
      importsToAdd.forEach(i -> {
         boolean exists = cu.getImports().stream()
                  .anyMatch(existing ->
                           !existing.isStatic() &&
                                    existing.getNameAsString().equals(i)
                  );
         if (!exists)
         {
            cu.addImport(i);
         }
      });
   }

   /**
    * Aggiunge import normale evitando duplicati
    */
   public void add_import(String className)
   {
      if (normalImports.add(className))
      {
         cu.addImport(className);
      }
   }

   /**
    * Aggiunge import static evitando duplicati
    */
   public void add_static_import(String className, String member)
   {
      String full = className + "." + member;
      if (staticImports.add(full))
      {
         // (name, isStatic, isAsterisk)
         cu.addImport(full, true, false);
      }
   }

   /**
    * Crea un FieldAccessExpr per accesso statico (Tipo.CAMPO)
    */
   public FieldAccessExpr static_field(Class<?> clazz, String field)
   {
      add_import(clazz.getName());
      return new FieldAccessExpr(new NameExpr(clazz.getSimpleName()), field);
   }

   /**
    * Crea un NameExpr dinamico (variabile o costante)
    */
   public NameExpr name(String name)
   {
      return new NameExpr(name);
   }

   /**
    * Aggiunge più import evitando duplicati
    */
   public void add_imports(List<String> importsToAdd)
   {
      importsToAdd.forEach(this::add_import);
   }

   /**
    * Converte {@code Class<?>} in {@code ClassOrInterfaceType} e aggiunge l'import.
    */
   public ClassOrInterfaceType class_type(Class<?> clazz)
   {
      add_import(clazz.getName());
      return new ClassOrInterfaceType(null, clazz.getSimpleName());
   }
}

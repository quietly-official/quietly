package ua.quietlymavenplugin.render.javaparser;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;

public class TestMethodFactory
{

   // esempio ("should_get_list_test", "void")
   public static MethodDeclaration newTestMethod(String name, String type)
   {
      MethodDeclaration m = new MethodDeclaration();
      m.setName(name);
      m.setType(type);
      m.addModifier(Modifier.Keyword.PUBLIC);
      m.addAnnotation("Test");
      return m;
   }

}

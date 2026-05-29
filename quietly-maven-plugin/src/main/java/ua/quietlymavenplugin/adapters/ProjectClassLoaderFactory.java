package ua.quietlymavenplugin.adapters;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class ProjectClassLoaderFactory {

   public static ClassLoader buildProjectClassLoader(MavenProject project) throws Exception {
      List<String> elements = project.getCompileClasspathElements();
      URL[] urls = new URL[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
         urls[i] = new File(elements.get(i)).toURI().toURL();
      }
      return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
   }

}

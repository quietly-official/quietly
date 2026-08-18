package ua.quietlymavenplugin.discovery;

import org.apache.maven.project.MavenProject;
import ua.quietlymavenplugin.adapters.ProjectClassLoaderFactory;
import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Optional;

public class ServiceResolver implements AutoCloseable
{

   private final QuietlyPluginConfig config;
   private final ClassLoader projectClassLoader;

   public ServiceResolver(MavenProject project, QuietlyPluginConfig config) throws Exception
   {
      this(config, ProjectClassLoaderFactory.buildProjectClassLoader(project));
   }

   ServiceResolver(QuietlyPluginConfig config, ClassLoader projectClassLoader)
   {
      this.config = config;
      this.projectClassLoader = projectClassLoader;
   }

   public Class<?> loadProjectClass(Class<?> scannedClass) throws ClassNotFoundException
   {
      return Class.forName(scannedClass.getName(), true, projectClassLoader);
   }

   public ServiceResolution resolve(Class<?> entityClass)
   {
      String servicePackage = config.resolveServicePackage(entityClass);
      String serviceSimpleName = config.resolveServiceName(entityClass);
      String expectedClassName = servicePackage + "." + serviceSimpleName;

      try
      {
         Class<?> serviceClass = Class.forName(expectedClassName, false, projectClassLoader);
         return new ServiceResolution(
                  expectedClassName,
                  servicePackage,
                  serviceSimpleName,
                  true,
                  Optional.of(serviceClass),
                  Optional.empty()
         );
      }
      catch (ClassNotFoundException e)
      {
         return new ServiceResolution(
                  expectedClassName,
                  servicePackage,
                  serviceSimpleName,
                  false,
                  Optional.empty(),
                  Optional.of("Expected " + expectedClassName + ". Configure servicePackagePattern/serviceNamePattern.")
         );
      }
   }

   @Override
   public void close() throws IOException
   {
      if (projectClassLoader instanceof URLClassLoader urlClassLoader)
      {
         urlClassLoader.close();
      }
   }
}

package ua.quietlymavenplugin.discovery;

import ua.quietlymavenplugin.render.config.QuietlyPluginConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FixtureResolver
{

   private final QuietlyPluginConfig config;

   public FixtureResolver(QuietlyPluginConfig config)
   {
      this.config = config;
   }

   public FixtureResolution resolve(Class<?> entityClass)
   {
      try
      {
         Object tableNameValue = entityClass.getField("TABLE_NAME").get(null);
         String tableName = String.valueOf(tableNameValue);
         Path sqlFixture = config.basedir().resolve("src/test/resources/sql/" + tableName + ".sql");
         return new FixtureResolution(
                  Optional.of(tableName),
                  Optional.of(sqlFixture),
                  Files.exists(sqlFixture),
                  Optional.empty()
         );
      }
      catch (NoSuchFieldException e)
      {
         return new FixtureResolution(
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  Optional.of("Entity does not expose public TABLE_NAME.")
         );
      }
      catch (Exception e)
      {
         return new FixtureResolution(
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  Optional.of("Could not inspect SQL fixture: " + e.getMessage())
         );
      }
   }
}

package ua.quietlytestsupport.support;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

public class SqlUtils {
   /**
    * Carica il contenuto di un singolo file SQL dal classpath.
    */
   public static String loadSqlFromClasspath(ClassLoader classLoader, String filePath) throws IOException {
      // rimuove un eventuale '/' iniziale dal percorso del file
      if (filePath.startsWith("/")) {
         filePath = filePath.substring(1);
      }

      // recupera l'URL del file dal classpath
      URL resourceUrl = classLoader.getResource(filePath);
      if (resourceUrl == null) {
         throw new IllegalStateException("File non trovato nel classpath: " + filePath);
      }

      try {
         // converte l'URL in un File e legge il contenuto
         File sqlFile = new File(resourceUrl.toURI());
         return FileUtils.readFileToString(sqlFile, Charset.defaultCharset());
      } catch (URISyntaxException | IOException e) {
         // lancia un'eccezione in caso di errore nel caricamento del file
         throw new IOException("Errore nel caricamento del file: " + filePath, e);
      }
   }

   /**
    * Carica il contenuto di un singolo file SQL dal classpath usando il ClassLoader corrente.
    */
   public static String loadSqlFromClasspath(String filePath) throws IOException {
      // usa il ClassLoader corrente per caricare il file
      return loadSqlFromClasspath(Thread.currentThread().getContextClassLoader(), filePath);
   }
}

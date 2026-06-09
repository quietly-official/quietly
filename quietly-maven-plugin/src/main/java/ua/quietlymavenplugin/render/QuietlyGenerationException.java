package ua.quietlymavenplugin.render;

public class QuietlyGenerationException extends RuntimeException
{

   public QuietlyGenerationException(String message)
   {
      super(message);
   }

   public QuietlyGenerationException(String message, Throwable cause)
   {
      super(message, cause);
   }
}

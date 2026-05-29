package ua.quietlymavenplugin.render.config;

public class Constants {

   public static final String QUIETLY_INFO = "["+ blueAnsiCode("QUIETLY") + "] ";
   public static final String QUIETLY_WARN = "["+ yellowAnsiCode("QUIETLY") + "] ";
   public static final String BLUE_BOLD_ASCII_START = "\u001B[1;34m";
   public static final String YELLOW_BOLD_ASCII_START = "\u001B[1;33m";
   public static final String END_ANSI_ESCAPE_CODE = "\u001B[0m";

   protected static String blueAnsiCode(String toFormat)
   {
      return BLUE_BOLD_ASCII_START + toFormat + END_ANSI_ESCAPE_CODE;
   }
   protected static String yellowAnsiCode(String toFormat)
   {
      return YELLOW_BOLD_ASCII_START + toFormat + END_ANSI_ESCAPE_CODE;
   }
}

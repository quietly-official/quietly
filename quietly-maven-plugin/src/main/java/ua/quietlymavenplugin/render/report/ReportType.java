package ua.quietlymavenplugin.render.report;

public enum ReportType
{
   FILTER_GENERATION("Quietly Filter Generation Report"),
   FILTER_SCAN("Quietly Filter Scan Report"),
   PROJECT_DIAGNOSTICS("Quietly Project Diagnostics Report"),
   CRUD_GENERATION("Quietly CRUD Generation Report");

   private final String title;

   ReportType(String title)
   {
      this.title = title;
   }

   public String title()
   {
      return title;
   }
}

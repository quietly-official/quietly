package ua.quietlymavenplugin.render.report;

public record QuietlyReportEntry(
         String entity,
         ReportCapability capability,
         String subject,
         String status,
         String details
)
{

   public String logicalKey()
   {
      return entity + "\u0000" + capability + "\u0000" + subject;
   }
}

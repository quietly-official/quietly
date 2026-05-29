package ua.quietlytestsupport.support;

public abstract class FilterTestBase extends ServiceRsTestUtilsV1 {

   /**
    * Smista la chiamata al metodo di test specifico in base al prefisso del filtro
    */
   protected <T> void assert_filter_works(String path, Object val, Class<T> clazz) {
      String pathLower = path.toLowerCase();

      if (pathLower.contains("like.")) {
         like_filter_test(path, val, clazz);
      } else if (pathLower.contains("from.")) {
         from_filter_test(path, val, clazz);
      } else if (pathLower.contains("to.")) {
         to_filter_test(path, val, clazz);
      } else if (pathLower.contains("nil.") && !pathLower.contains("not_nil.")) {
         nil_filter_test(path, clazz);
      } else if (pathLower.contains("not_nil.")) {
         not_nil_filter_test(path, clazz);
      } else {
         obj_filter_test(path, val, clazz);
      }
   }
}

package com.smarthub.plus;
import java.text.Normalizer;import java.util.*;
/** Broadcaster placeholders are availability states, never titles for poster/subtitle lookup. */
final class ProgrammeTitle {
 static boolean available(String title){if(title==null)return false;String s=Normalizer.normalize(title,Normalizer.Form.NFKC).replaceAll("[\\p{Cf}]","").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s.\\-–—…]+"," ").trim();return !s.isEmpty()&&!new HashSet<>(Arrays.asList("no information","no info","no epg","no epg information","no program information","no programme information","programme unavailable","program unavailable","epg not available","n/a","no data","لا يوجد دليل البرامج","لا يوجد دليل البرنامج","لا توجد معلومات","لا يوجد معلومات","لا تتوفر معلومات","دليل البرامج غير متاح","بيانات البرنامج غير متاحة","غير متاح من epg","غير متاح")).contains(s);}
}

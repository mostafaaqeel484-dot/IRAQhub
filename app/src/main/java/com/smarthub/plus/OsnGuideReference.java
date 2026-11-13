package com.smarthub.plus;

import java.util.Locale;

/** Exact public OSN channel-guide links. Reference only, never an unverified EPG API. */
final class OsnGuideReference {
 private static final String PREFIX="https://www.osn.com/en-iq/explore/channels/";
 private OsnGuideReference(){}
 static String urlFor(String channel){
  if(channel==null)return "";
  String name=channel.toUpperCase(Locale.ROOT).trim()
    .replaceAll("\\s+"," ").replaceAll("(?: HD| FHD| UHD)$","");
  // Only exact verified OSN channels; never match unrelated similar names.
  switch(name){
   case "OSN YAHALA AFLAM":case "OSNTV YAHALA AFLAM":return PREFIX+"osntv-yahala-aflam";
   case "OSN YAHALA":case "OSNTV YAHALA":return PREFIX+"osntv-yahala";
   case "OSN NOW":case "OSNTV NOW":return PREFIX+"osntv-now";
   case "OSN MOVIES FAMILY":case "OSNTV MOVIES FAMILY":return PREFIX+"osntv-movies-family";
   case "OSN SHOWCASE":case "OSNTV SHOWCASE":return PREFIX+"osntv-showcase";
   case "OSN KIDS":case "OSNTV KIDS":return PREFIX+"osntv-kids";

   case "OSN COMEDY":case "OSNTV COMEDY":return PREFIX+"osntv-comedy";
   case "OSN MOVIES PREMIERE":case "OSNTV MOVIES PREMIERE":return PREFIX+"osntv-movies-premiere";
   case "OSN MOVIES ACTION":case "OSNTV MOVIES ACTION":return PREFIX+"osntv-movies-action";
   case "OSN MOVIES HORROR":case "OSNTV MOVIES HORROR":return PREFIX+"osntv-movies-horror";
   case "OSN MOVIES HOLLYWOOD":case "OSNTV MOVIES HOLLYWOOD":return PREFIX+"osntv-movies-hollywood";
   case "OSN POP UP":case "OSNTV POP UP":return PREFIX+"osntv-pop-up";
   default:return "";
  }
 }
 static String channels(){return "https://www.osn.com/en-iq/explore/channels-sitemap";}
}

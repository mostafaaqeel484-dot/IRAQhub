package com.smarthub.plus;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Safe, best-effort entry to the original OEM search navigation.
 * The NavHost action is not an Android standard and is absent on some ROMs.
 * Never claim that this launches a scan, or fake a search animation.
 */
final class NativeSearchNavigation {
 private NativeSearchNavigation(){}
 static boolean open(HubActivity host){
  String error="";
  // Verified by the owner's live ADB capture as the actual Aurum installation UI.
  try{
   Intent direct=new Intent(Intent.ACTION_MAIN)
     .setClassName("com.tosmart.aurum","com.tosmart.dvb.common.NavHostActivity");
   host.startActivity(direct);
   return true;
  }catch(Exception e){error=e.getClass().getSimpleName();}
  try{
   Intent home=host.getPackageManager().getLaunchIntentForPackage("com.tosmart.aurum");
   if(home!=null){
    host.startActivity(home);
    host.toast(host.tx("فتحت Aurum. اختر التركيب ثم نوع البحث بالريموت.",
      "Aurum opened. Select Installation and scan mode with the remote."));
    return true;
   }
  }catch(Exception e){error=e.getClass().getSimpleName();}
  host.prefs().edit().putString("native_search_launch_error",
    "OEM_SEARCH_ACTIVITY_UNAVAILABLE_"+error).apply();
  host.toast(host.tx("تعذر فتح بحث المصنع من IRAQhub. افتح التركيب من Aurum مباشرةً.",
    "The firmware blocked this menu. Open Installation directly in Aurum."));
  return false;
 }
}

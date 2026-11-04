package com.smarthub.plus;
import android.accessibilityservice.AccessibilityServiceInfo;import android.content.*;import android.provider.Settings;import android.view.accessibility.AccessibilityManager;

/** Uses Android TV's public OEM service-detail hook, falling back to standard settings. */
final class AccessibilitySetup {
 static void open(Context c){
  AccessibilityManager manager=c.getSystemService(AccessibilityManager.class);
  boolean hook=false;try{android.content.res.Resources r=c.createPackageContext("com.android.tv.settings",0).getResources();int id=r.getIdentifier("config_tvSettingsFlavor","string","com.android.tv.settings");String flavor=id==0?"Classic":r.getString(id);hook=flavor.equals("X")||flavor.equals("Vendor");}catch(Exception unavailable){}
  if(hook&&manager!=null)for(AccessibilityServiceInfo info:manager.getInstalledAccessibilityServiceList())if(info.getResolveInfo().serviceInfo.packageName.equals(c.getPackageName())){
   try{c.startActivity(new Intent("android.settings.ACCESSIBILITY_SHORTCUT_SERVICES_TV_OEM_LINK").putExtra("accessibilityServiceInfo",info));return;}catch(ActivityNotFoundException|SecurityException unavailable){}
  }
  c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
 }
}

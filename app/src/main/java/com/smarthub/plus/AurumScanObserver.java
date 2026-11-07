package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Observe only a user-enabled Aurum search result; never click, tune or write OEM data. */
final class AurumScanObserver {
 private static final String PKG="com.tosmart.aurum";
 private static final String PREFIX=PKG+":id/";
 private static final Set<String> ALLOWED=new HashSet<>(Arrays.asList(
   "header_title","tv_detail_title","tv_radio_title",
   "tv_search_result_title","tv_search_result_info",
   "channel_all_total_count","tv_tips"));
 private AurumScanObserver(){}

 static void observe(Context context,AccessibilityNodeInfo root){
  if(context==null||root==null||!PKG.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return;
  SharedPreferences prefs=context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);
  if(!prefs.getBoolean("native_scan_observation_enabled",false))return;
  List<AccessibilityNodeInfo> flags=root.findAccessibilityNodeInfosByViewId(PREFIX+"tv_tips");
  boolean visible=false;
  if(flags!=null)for(AccessibilityNodeInfo node:flags)try{
   if(node!=null&&node.isVisibleToUser())visible=true;
  }finally{if(node!=null)node.recycle();}
  if(!visible)return;
  List<AurumScanSummary.Field> fields=new ArrayList<>();
  gather(root,fields,0,new int[]{0});
  AurumScanSummary.Result result=AurumScanSummary.parse(fields,System.currentTimeMillis());
  if(result==null)return;
  String identity=result.identity();
  if(identity.equals(prefs.getString("native_scan_last_identity","")))return;
  try{
   prefs.edit().putString("native_scan_last_identity",identity)
     .putString("native_scan_last_result",result.json().toString())
     .putLong("native_scan_last_observed_at",result.observedAt)
     .apply();
  }catch(Exception ignored){}
 }
 private static void gather(AccessibilityNodeInfo node,List<AurumScanSummary.Field> out,
                            int depth,int[] count){
  if(node==null||depth>24||count[0]++>550||out.size()>120)return;
  if(node.isVisibleToUser()){
   String id=node.getViewIdResourceName();
   if(id!=null&&id.startsWith(PREFIX)&&ALLOWED.contains(id.substring(PREFIX.length()))){
    CharSequence text=node.getText();
    out.add(new AurumScanSummary.Field(id,text==null?"":text.toString()));
   }
  }
  for(int i=0;i<node.getChildCount()&&count[0]<550;i++){
   AccessibilityNodeInfo child=node.getChild(i);
   if(child!=null)try{gather(child,out,depth+1,count);}finally{child.recycle();}
  }
 }
}

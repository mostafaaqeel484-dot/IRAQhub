package com.smarthub.plus;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
/** Structural allowlist: captions are read from the playback screen, never receiver menus. */
final class NativeTvUi {
 private static final Set<String> MENUS=new HashSet<>(Arrays.asList("channel_list","setting_dialog_frameLayout","desttop_popup_list_frameLayout","rv_epg_list","subtitle_list"));
 private static final Set<String> TEXT=new HashSet<>(Arrays.asList("subtitle_text","ext_subtext","rl_sub_txt"));
 static boolean live(AccessibilityNodeInfo root){return root!=null&&SatelliteUiParser.DEFAULT_PACKAGE.contentEquals(root.getPackageName())&&safe(root,0,new int[]{0});}
 private static boolean safe(AccessibilityNodeInfo n,int depth,int[] count){if(depth>24||count[0]++>800||n.isEditable()||n.isPassword())return false;String id=shortId(n);if(n.isVisibleToUser()&&(id.equals("tv_epg_detail_title")||MENUS.contains(id)&&n.getChildCount()>0))return false;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{if(!safe(c,depth+1,count))return false;}finally{c.recycle();}}return true;}
 static String shortId(AccessibilityNodeInfo n){String id=n.getViewIdResourceName();return id!=null&&id.startsWith(SatelliteUiParser.DEFAULT_PACKAGE+":id/")?id.substring(id.indexOf(":id/")+4):"";}
 static String captionText(AccessibilityNodeInfo root){StringBuilder out=new StringBuilder();collect(root,out,false,0,new int[]{0});return out.toString().trim();}
 private static void collect(AccessibilityNodeInfo n,StringBuilder out,boolean inside,int depth,int[] count){if(n==null||depth>24||count[0]++>800||out.length()>800)return;boolean caption=inside||TEXT.contains(shortId(n));if(caption&&n.isVisibleToUser()&&!n.isPassword()&&!n.isEditable()&&n.getText()!=null&&!n.getText().toString().trim().isEmpty()){if(out.length()>0)out.append('\n');out.append(n.getText());}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{collect(c,out,caption,depth+1,count);}finally{c.recycle();}}}
}

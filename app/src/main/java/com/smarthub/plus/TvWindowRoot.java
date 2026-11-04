package com.smarthub.plus;
import android.accessibilityservice.AccessibilityService;import android.view.accessibility.*;import java.util.*;
/** Non-focusable app overlays must not replace the focused TV application as the observation source. */
final class TvWindowRoot {
 static AccessibilityNodeInfo find(AccessibilityService service){AccessibilityNodeInfo active=service.getRootInActiveWindow();if(isTv(active))return active;if(active!=null)active.recycle();List<AccessibilityWindowInfo> windows=service.getWindows();AccessibilityNodeInfo first=null;try{for(AccessibilityWindowInfo w:windows){if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION&&!w.isFocused())continue;AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;if(w.isFocused()){if(first!=null){first.recycle();first=null;}if(isTv(root))return root;root.recycle();return null;}if(first==null)first=root;else root.recycle();}if(isTv(first)){AccessibilityNodeInfo result=first;first=null;return result;}return null;}finally{if(first!=null)first.recycle();for(AccessibilityWindowInfo w:windows)w.recycle();}}
 private static boolean isTv(AccessibilityNodeInfo root){return root!=null&&SatelliteUiParser.DEFAULT_PACKAGE.contentEquals(root.getPackageName()==null?"":root.getPackageName());}
}

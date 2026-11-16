package com.smarthub.plus;

import android.net.Uri;

/** Stable launch/manifest entry, now powered by the native TV directory. */
public final class LiveFeedActivity extends LiveFeedNativeActivity {
 static boolean trustedGuide(String address){
  try {
   Uri uri=Uri.parse(address);
   return "https".equalsIgnoreCase(uri.getScheme())&&uri.getUserInfo()==null&&
    ("live-feed.net".equalsIgnoreCase(uri.getHost())||
     "www.live-feed.net".equalsIgnoreCase(uri.getHost()));
  }catch(Exception bad){return false;}
 }
}

package com.smarthub.plus;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Optional, user-launched Live Feed satellite guide.
 * This is an isolated website viewer, not a scraper, feed decoder, BISS key importer,
 * tuner controller, or claim of a public live-feed.net API. All navigation is HTTPS.
 */
public final class LiveFeedActivity extends HubActivity {
 private static final String HOME="https://live-feed.net/";
 private WebView browser;
 private TextView status;
 private boolean destroyed,mainFrameFailed;

 static boolean trustedGuide(String address){
  try{
   Uri link=Uri.parse(address);
   String host=link.getHost();
   return "https".equalsIgnoreCase(link.getScheme()) && link.getUserInfo()==null
     && ("live-feed.net".equalsIgnoreCase(host)||"www.live-feed.net".equalsIgnoreCase(host));
  }catch(Exception invalid){return false;}
 }

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  LinearLayout root=col();root.setBackgroundColor(0xff0b1019);
  root.setPadding(dp(12),dp(8),dp(12),dp(8));
  LinearLayout toolbar=row();toolbar.setGravity(Gravity.CENTER_VERTICAL);
  TextView heading=text("LIVE FEED  ·  Satellite Guide",19,true);
  toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Button refresh=button(tx("تحديث","Refresh"));
  refresh.setOnClickListener(v->{if(browser!=null)browser.reload();});
  toolbar.addView(refresh,new LinearLayout.LayoutParams(dp(108),dp(43)));
  Button allFeeds=button(tx("كل الفيدات","All Feeds"));
  allFeeds.setOnClickListener(v->{if(browser!=null)browser.loadUrl(HOME+"?show_off=1");});
  toolbar.addView(allFeeds,new LinearLayout.LayoutParams(dp(122),dp(43)));
  Button close=button(tx("رجوع","Back"));
  close.setOnClickListener(v->finish());
  toolbar.addView(close,new LinearLayout.LayoutParams(dp(108),dp(43)));
  root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(50)));
  status=text(tx("جاري فتح دليل الفيدات…","Opening satellite feed guide…"),13,false);
  status.setTextColor(0xffc6c9cf);
  status.setGravity(Gravity.CENTER_VERTICAL);
  root.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
  try{browser=new WebView(this);}catch(RuntimeException unavailable){
   status.setText(tx("WebView غير متوفر على هذا الجهاز","WebView is unavailable on this receiver"));
   Button external=button(tx("فتح الموقع بالمتصفح","Open in external browser"));
   external.setOnClickListener(v->{
    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(HOME)));}
    catch(ActivityNotFoundException absent){toast(tx("لا يوجد متصفح مثبت","No browser installed"));}
   });
   root.addView(external,new LinearLayout.LayoutParams(-1,dp(54)));
   setContentView(root);return;
  }
  browser.setBackgroundColor(Color.BLACK);
  browser.setFocusable(true);browser.setFocusableInTouchMode(true);
  WebSettings settings=browser.getSettings();
  settings.setJavaScriptEnabled(true); // The website renders its own filters/cards.
  settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);
  settings.setAllowContentAccess(false);
  settings.setAllowFileAccessFromFileURLs(false);
  settings.setAllowUniversalAccessFromFileURLs(false);
  settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setSupportMultipleWindows(false);
  settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setMediaPlaybackRequiresUserGesture(true);
  settings.setBuiltInZoomControls(true);
  settings.setDisplayZoomControls(false);
  browser.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri url=request.getUrl();
    if(trustedGuide(url.toString()))return false;
    if(!"https".equalsIgnoreCase(url.getScheme()))return true;
    try{startActivity(new Intent(Intent.ACTION_VIEW,url));}
    catch(ActivityNotFoundException unavailable){toast(tx("المتصفح الخارجي غير متاح","External browser unavailable"));}
    return true;
   }
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){
    mainFrameFailed=false;
   }
   @Override public void onPageFinished(WebView view,String url){
    if(!destroyed&&!mainFrameFailed)status.setText(tx("الدليل مباشر من live-feed.net · BACK للرجوع",
      "Live website · use BACK to exit"));
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
    if(!destroyed&&request.isForMainFrame()){
     mainFrameFailed=true;
     status.setText(tx("تعذر تحميل الموقع؛ تحقق من الإنترنت ثم اضغط تحديث",
      "Website unavailable. Check internet and press Refresh."));
    }
   }
  });
  root.addView(browser,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
  setContentView(root);
  browser.loadUrl(HOME);
  browser.requestFocus();
 }
 @Override public void onBackPressed(){
  if(browser!=null&&browser.canGoBack()){browser.goBack();return;}
  super.onBackPressed();
 }
 @Override protected void onPause(){if(browser!=null)browser.onPause();super.onPause();}
 @Override protected void onResume(){super.onResume();if(browser!=null)browser.onResume();}
 @Override protected void onDestroy(){
  destroyed=true;
  if(browser!=null){
   browser.stopLoading();
   browser.loadUrl("about:blank");
   if(browser.getParent() instanceof ViewGroup)((ViewGroup)browser.getParent()).removeView(browser);
   browser.destroy();browser=null;
  }
  super.onDestroy();
 }
}

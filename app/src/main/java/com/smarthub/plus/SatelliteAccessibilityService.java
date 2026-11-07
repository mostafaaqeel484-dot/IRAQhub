package com.smarthub.plus;

import android.accessibilityservice.*;
import android.content.*;
import android.os.*;
import android.view.KeyEvent;
import android.view.accessibility.*;
import java.time.ZoneId;
import java.util.*;

/** User-enabled channel/EPG observation; Menu and optional Blue shortcuts handle only
 * the configured TV app. Other app text and remote keys are never recorded. */
public final class SatelliteAccessibilityService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final MenuKeyHandler menuKey=new MenuKeyHandler(this::openLauncher);
    private CHMAXIntegration chmax;private NativeCaptionBridge captions;private NativeGuideArtwork guideArtwork;private SharedPreferences prefs;private String tvPackage=SatelliteUiParser.DEFAULT_PACKAGE;private boolean queued,closed,blueHeld;
    private final Runnable capture=new Runnable(){public void run(){queued=false;if(!closed)try{readBanner();}finally{if(!queued&&!closed){queued=true;handler.postDelayed(this,1000);}}}};
    private void openLauncher(){boolean opened=ReceiverNavigation.openLauncher(this);if(prefs!=null)prefs.edit().putString("satellite_shortcut_error",opened?"":"Unable to open IRAQhub home").apply();}
    @Override protected void onServiceConnected(){super.onServiceConnected();closed=false;prefs=getSharedPreferences("smarthub",MODE_PRIVATE);if(!prefs.getBoolean("accessibility_safe_menu_v1",false)){
        // One-time safety migration: enabling accessibility must never hijack MENU.
        // The user can explicitly turn the shortcut back on in settings.
        prefs.edit().putBoolean("satellite_menu_shortcut",false)
         .putBoolean("accessibility_safe_menu_v1",true).apply();
    }
    prefs.registerOnSharedPreferenceChangeListener(this);configure();prefs.edit().putBoolean("satellite_accessibility_connected",true).putBoolean("satellite_tv_visible",false).apply();captions=new NativeCaptionBridge(this);schedule();}
    private void configure(){
        String selected=prefs.getString("satellite_tv_package",SatelliteUiParser.DEFAULT_PACKAGE);tvPackage=selected!=null&&selected.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")?selected:SatelliteUiParser.DEFAULT_PACKAGE;
        // Window-state package names let the companion hide outside TV. Content
        // from other packages is discarded before obtaining any source/root text.
        AccessibilityServiceInfo info=getServiceInfo();if(info==null)info=new AccessibilityServiceInfo();info.packageNames=null;info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED|AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.notificationTimeout=180;info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        if(prefs.getBoolean("satellite_menu_shortcut",false)||prefs.getBoolean("satellite_blue_shortcut",false))info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;else info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;setServiceInfo(info);
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences source,String key){if("satellite_tv_package".equals(key)){configure();schedule();}else if("satellite_blue_shortcut".equals(key)||"satellite_menu_shortcut".equals(key))configure();}
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_MENU){
            if(event.getAction()==KeyEvent.ACTION_UP)return menuKey.up();
            if(event.getAction()==KeyEvent.ACTION_DOWN)return menuKey.down(!closed&&prefs!=null&&prefs.getBoolean("satellite_menu_shortcut",false)&&tvActive(),event.getRepeatCount());
            return false;
        }
        if(event==null||event.getKeyCode()!=KeyEvent.KEYCODE_PROG_BLUE)return false;
        if(event.getAction()==KeyEvent.ACTION_UP){boolean consumed=blueHeld;blueHeld=false;return consumed;}
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return blueHeld;
        if(blueHeld)return true;
        if(closed||prefs==null||event.getRepeatCount()!=0||!prefs.getBoolean("satellite_blue_shortcut",false)||!prefs.getBoolean("satellite_companion_enabled",false)||!prefs.getBoolean("satellite_tv_visible",false)||!tvActive())return false;
        // Consume the whole opted-in Blue press, including repeats and release,
        // so the native receiver does not also perform its normal Blue action.
        blueHeld=true;
        try{
            startForegroundService(new Intent().setClassName(getPackageName(),"com.smarthub.plus.SatelliteCompanionService").setAction("com.smarthub.plus.satellite.OPEN"));
            prefs.edit().putString("satellite_shortcut_error","").apply();
        }catch(RuntimeException failure){prefs.edit().putString("satellite_shortcut_error","Could not open the satellite companion. Check overlay permission and start it from IRAQhub.").apply();}
        return true;
    }
    private boolean tvActive(){AccessibilityNodeInfo root=null;try{root=TvWindowRoot.find(this);return root!=null&&tvPackage.contentEquals(root.getPackageName()==null?"":root.getPackageName());}catch(RuntimeException unavailable){return false;}finally{if(root!=null)root.recycle();}}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||prefs==null)return;String eventPackage=event.getPackageName()==null?"":event.getPackageName().toString();
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            if(tvPackage.equals(eventPackage))visible(true);
            else if(!eventPackage.isEmpty()&&!eventPackage.equals("android")&&!eventPackage.equals("com.android.systemui")){
                // A non-focusable SmartHub bubble can emit window events while
                // the TV remains the active app; examine package identity only.
                if(!(eventPackage.equals(getPackageName())&&prefs.getBoolean("satellite_companion_expanded",false))){
                    boolean tvActive=false;if(eventPackage.equals(getPackageName())){AccessibilityNodeInfo root=TvWindowRoot.find(this);if(root!=null)try{tvActive=tvPackage.contentEquals(root.getPackageName()==null?"":root.getPackageName());}finally{root.recycle();}}
                    visible(tvActive);
                }
            }
        }
        if(tvPackage.equals(eventPackage)){schedule();/* Read-only EPG/Infobar observation; no OEM menu automation. */}
    }
    private void visible(boolean value){if(prefs.getBoolean("satellite_tv_visible",false)!=value)prefs.edit().putBoolean("satellite_tv_visible",value).apply();}
    private void schedule(){if(!queued&&!closed){queued=true;handler.postDelayed(capture,220);}}
    private void readBanner(){
        AccessibilityNodeInfo root=TvWindowRoot.find(this);if(root==null)return;
        try{
            if(!tvPackage.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return;visible(true);
            // Opt-in, read-only OEM scan results observed on the genuine Aurum screen.
            // No native search commands, remote keys or firmware changes are issued here.
            AurumScanObserver.observe(this,root);
            if(guideArtwork==null)guideArtwork=new NativeGuideArtwork(this);guideArtwork.observe(root);
            Map<String,String> fields=new HashMap<>();collect(root,fields,0,new int[]{0});long now=System.currentTimeMillis();SatelliteState parsed=SatelliteUiParser.parse(fields,now,ZoneId.of(prefs.getString("tv_epg_timezone","Asia/Baghdad")),tvPackage);prefs.edit().putBoolean("satellite_native_banner_visible",parsed!=null).apply();
            SatelliteState old=SatelliteState.read(this);if(chmax==null)chmax=new CHMAXIntegration(this);chmax.observe(root,parsed==null?old:parsed,fields);
            NativeTrackInfo.observe(this,root,parsed==null?old:parsed);if(parsed!=null){OsdGeometry.observe(this,root);BroadcastBadges.observe(this,root,parsed);ReceptionData.observe(this,parsed,fields);}
            else if(ReceptionData.belongsTo(old,fields))ReceptionData.observe(this,old,fields);
            if(parsed==null)return;
            // A fresh published data provider is richer than the fallback info banner.
            if(SatelliteUiParser.sameChannel(old,parsed)&&!old.source.isEmpty()&&!old.source.startsWith("accessibility:")&&old.observedAt>now-30_000&&old.observedAt<=now)return;
            if(SatelliteUiParser.sameChannel(old,parsed)&&old.source.startsWith("accessibility:")){
                if(!SatelliteUiParser.currentProvided(fields,tvPackage)){parsed.title=old.title;parsed.startMs=old.startMs;parsed.endMs=old.endMs;}
                if(!SatelliteUiParser.nextProvided(fields,tvPackage)){parsed.nextTitle=old.nextTitle;parsed.nextStartMs=old.nextStartMs;parsed.nextEndMs=old.nextEndMs;}
            }
            if(SatelliteUiParser.sameChannel(old,parsed)){if(parsed.title.equals(old.title)&&parsed.startMs==old.startMs)parsed.image=old.image;if(parsed.nextTitle.equals(old.nextTitle)&&parsed.nextStartMs==old.nextStartMs)parsed.nextImage=old.nextImage;}
            parsed.expire(now);parsed.save(this);prefs.edit().putLong("satellite_accessibility_last_observed_at",now).putString("satellite_accessibility_error","").apply();
        }catch(RuntimeException failure){if(prefs!=null)prefs.edit().putString("satellite_accessibility_error","Unable to read the visible receiver information banner").apply();}
        finally{root.recycle();}
    }
    private void collect(AccessibilityNodeInfo node,Map<String,String> out,int depth,int[] count){
        if(node==null||depth>24||count[0]++>600)return;String id=node.getViewIdResourceName();
        if(id!=null&&id.startsWith(tvPackage+":id/")&&node.isVisibleToUser()){
            String shortId=id.substring((tvPackage+":id/").length());if(SatelliteUiParser.VIEW_IDS.contains(shortId)||ReceptionData.accepts(shortId)||CHMAXIntegration.TEXT_IDS.contains(shortId)||(("tv_program_name".equals(shortId)||"tv_name".equals(shortId)||"tv_program_index".equals(shortId)||"tv_index".equals(shortId))&&node.isSelected())){CharSequence text=node.getText();if(text!=null){String value=text.toString();out.put(("tv_program_name".equals(shortId)||"tv_name".equals(shortId))?"selected_channel":("tv_program_index".equals(shortId)||"tv_index".equals(shortId))?"selected_channel_index":shortId,value.substring(0,Math.min(2000,value.length())));}}
        }
        for(int i=0;i<node.getChildCount()&&count[0]<600;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)try{collect(child,out,depth+1,count);}finally{child.recycle();}}
    }
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent intent){disconnect();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
    private void disconnect(){menuKey.reset();if(captions!=null){captions.close();captions=null;}closed=true;queued=false;blueHeld=false;handler.removeCallbacksAndMessages(null);if(prefs!=null){prefs.unregisterOnSharedPreferenceChangeListener(this);prefs.edit().putBoolean("satellite_accessibility_connected",false).putBoolean("satellite_tv_visible",false).apply();}}
}

package com.smarthub.plus;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

final class LauncherAcceptance30 {
    private static Object field(Object obj,String name)throws Exception{Field f=NowNextView.class.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private static Rect rect(NowNextView root,View view){Rect r=new Rect();view.getDrawingRect(r);root.offsetDescendantRectToMyCoords(view,r);return r;}
    private static GridView grid(View v){if(v instanceof GridView)return (GridView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){GridView g=grid(((ViewGroup)v).getChildAt(i));if(g!=null)return g;}return null;}
    static void run(Instrumentation test,Context c,Bundle out)throws Exception{
        out.putInt("normal_app_uid",android.os.Process.myUid());out.putString("api",Integer.toString(Build.VERSION.SDK_INT));out.putString("soc",Build.SOC_MODEL);out.putString("abi",String.join(",",Build.SUPPORTED_ABIS));
        List<AppCatalogue.App> apps=AppCatalogue.load(c);Set<String> distinct=new HashSet<>();boolean complete=true;for(AppCatalogue.App app:apps){complete&=!app.label.isEmpty()&&app.info.activityInfo.exported;distinct.add(app.pkg);}out.putInt("launchable_apps",apps.size());out.putBoolean("apps_deduplicated",distinct.size()==apps.size());out.putBoolean("exported_apps_only",complete);
        ReceiverChannelRepository.Result channels=ReceiverChannelRepository.load(c);out.putInt("channel_rows",channels.channels.size());out.putString("public_channel_status",channels.publicStatus);out.putBoolean("native_tv_resolves",ReceiverNavigation.available(c,ReceiverNavigation.television()));out.putBoolean("native_menu_resolves",ReceiverNavigation.available(c,ReceiverNavigation.menu()));out.putBoolean("native_epg_resolves",ReceiverNavigation.available(c,ReceiverNavigation.guide()));out.putBoolean("original_home_resolves",ReceiverNavigation.available(c,ReceiverNavigation.originalHome()));out.putBoolean("iraqhub_home_resolves",c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(c.getPackageName()),0)!=null);
        Activity home=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        test.runOnMainSync(()->{View tile=home.getWindow().getDecorView().findViewWithTag("live-tv");if(tile==null)throw new AssertionError("Missing live TV tile");tile.requestFocus();out.putBoolean("live_tile_focused",tile.hasFocus());});
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);test.waitForIdleSync();test.runOnMainSync(()->{View focused=home.getCurrentFocus();out.putBoolean("dpad_navigation",focused!=null&&!"live-tv".equals(focused.getTag()));});
        Activity appPage=test.startActivitySync(new Intent(c,AppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));GridView[] found={null};for(int i=0;i<40;i++){Thread.sleep(100);test.runOnMainSync(()->found[0]=grid(appPage.getWindow().getDecorView()));if(found[0]!=null&&found[0].getAdapter()!=null&&found[0].getAdapter().getCount()>0)break;}
        test.runOnMainSync(()->out.putInt("rendered_app_tiles",found[0]==null||found[0].getAdapter()==null?0:found[0].getAdapter().getCount()));
        test.runOnMainSync(()->{try{SatelliteState s=SatelliteState.read(c);NowNextView view=new NowNextView(c);view.bind(s,null,null,null,"");int w=c.getResources().getDisplayMetrics().widthPixels,h=OsdGeometry.window(c);view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);Rect quality=rect(view,(View)field(view,"qualityBar")),detail=rect(view,(View)field(view,"signalDetail"));out.putBoolean("snr_ber_below_meters",detail.top>=quality.bottom&&detail.bottom<=h);out.putBoolean("snr_ber_not_in_broadcast_row",!((TextView)field(view,"technical")).getText().toString().contains("SNR"));out.putBoolean("encryption_separate",((TextView)field(view,"encryptionLine")).getVisibility()==View.VISIBLE);}catch(Exception e){throw new RuntimeException(e);}});
        String report=CompatibilityActivity.inspect(c);try(FileOutputStream f=new FileOutputStream(new File(c.getCacheDir(),"compatibility30.txt"))){f.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        if(apps.isEmpty()||!complete||!out.getBoolean("iraqhub_home_resolves")||!out.getBoolean("dpad_navigation")||!out.getBoolean("snr_ber_below_meters"))throw new IOException("Launcher acceptance failed");
    }
    static void openSettings(Instrumentation test,Context c,Bundle out)throws Exception {for(AppCatalogue.App app:AppCatalogue.load(c))if(app.pkg.equals("com.droidlogic.tv.settings")){Activity home=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.runOnMainSync(()->home.startActivity(AppCatalogue.launch(app)));out.putString("opened_app_package",app.pkg);out.putBoolean("settings_launch_requested",true);return;}throw new IOException("Settings entry unavailable");}
    static void nativeOpen(Instrumentation test,Context c,Bundle args,Bundle out)throws Exception {String destination=args.getString("destination","");Intent intent=destination.equals("menu")?ReceiverNavigation.menu():destination.equals("guide")?ReceiverNavigation.guide():destination.equals("home")?ReceiverNavigation.originalHome():ReceiverNavigation.television();Activity home=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.runOnMainSync(()->out.putBoolean("native_open",ReceiverNavigation.open(home,intent)));Thread.sleep(700);out.putString("destination",destination);if(!out.getBoolean("native_open"))throw new IOException("Native activity denied");}
    private static Object channelField(Activity a,String name)throws Exception{Field f=ReceiverChannelsActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    static void channels(Instrumentation test,Context c,Bundle out)throws Exception{
        Activity a=test.startActivitySync(new Intent(c,ReceiverChannelsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->{try{((EditText)channelField(a,"query")).setText("MBC2");}catch(Exception e){throw new RuntimeException(e);}});
        int[] count={0};for(int n=0;n<80;n++){Thread.sleep(250);test.runOnMainSync(()->{try{count[0]=((List<?>)channelField(a,"shown")).size();}catch(Exception e){throw new RuntimeException(e);}});if(count[0]>0)break;}
        test.runOnMainSync(()->{try{out.putBoolean("loader_completed",(Boolean)channelField(a,"loaded"));out.putInt("load_generation",(Integer)channelField(a,"loadGeneration"));out.putInt("search_generation",(Integer)channelField(a,"generation"));out.putInt("loaded_channel_count",((List<?>)channelField(a,"all")).size());out.putInt("filtered_count",count[0]);out.putString("channel_status",((TextView)channelField(a,"status")).getText().toString());}catch(Exception e){throw new RuntimeException(e);}});
        if(count[0]==0){StringBuilder trace=new StringBuilder();for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){boolean ours=false;for(StackTraceElement s:entry.getValue())if(s.getClassName().contains("ReceiverChannel")||s.getClassName().contains("ChlLogoAddon"))ours=true;if(ours){trace.append(entry.getKey().getName()).append(' ').append(entry.getKey().getState()).append('\n');for(StackTraceElement s:entry.getValue())trace.append(s).append('\n');}}out.putString("channel_threads",trace.toString());throw new IOException("Channel library did not publish search results");}
        out.putBoolean("typed_before_load_supported",true);
    }
}

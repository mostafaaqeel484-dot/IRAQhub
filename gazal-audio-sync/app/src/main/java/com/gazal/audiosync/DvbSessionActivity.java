package com.gazal.audiosync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental in-app TV Input session. Unlike overlay over Aurum, this activity owns its
 * TvView session and may call Android's official timeShiftPause/Resume/SeekTo functions.
 * The OEM TV input must expose channels and implement TimeShift for this to work.
 */
public final class DvbSessionActivity extends Activity {
    private static final class Channel {
        final String name,input;
        final Uri uri;
        Channel(String name,String input,Uri uri){this.name=name;this.input=input;this.uri=uri;}
    }
    private final ArrayList<Channel> channels=new ArrayList<>();
    private TvView tv;
    private TextView status, channelLabel;
    private int index = 0, delaySeconds = 5;
    private long currentPosition = -1, startPosition = -1;
    private boolean hasTimeShiftPosition=false;
    private String selectedInput = "";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TvView(this);
        root.addView(tv,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(10,7,10,7);
        banner.setBackgroundColor(0xDA101E31);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        root.addView(banner,lp);
        channelLabel=label("Gazal TV Input • اختر قناة",16);
        banner.addView(channelLabel);
        status=label("TimeShift: غير مختبر • دعم واجهة TV Input يتوقف على Aurum/DTVKit",13);
        banner.addView(status);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        add(controls,"◀ CH",(v)->change(-1));
        add(controls,"▶ CH",(v)->change(1));
        add(controls,"LIVE",(v)->tune());
        add(controls,"PAUSE VIDEO",(v)->pause());
        add(controls,"RESUME",(v)->resume());
        add(controls,"−1s",(v)->changeDelay(-1));
        add(controls,"+1s",(v)->changeDelay(1));
        add(controls,"APPLY",(v)->applyDelay());
        add(controls,"RETURN",(v)->finish());
        banner.addView(controls);
        setContentView(root);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String inputId) {
                setStatus("TV Input connection FAILED: "+inputId);
            }
            @Override public void onDisconnected(String inputId) {
                setStatus("TV Input disconnected: "+inputId);
            }
            @Override public void onVideoAvailable(String inputId) {
                setStatus("VIDEO AVAILABLE • "+inputId+" • requested "+delaySeconds+"s");
            }
            @Override public void onVideoUnavailable(String inputId,int reason) {
                setStatus("Video unavailable: reason "+reason+" on "+inputId);
            }
        });
        tv.setTimeShiftPositionCallback(new TvView.TimeShiftPositionCallback(){
            @Override public void onTimeShiftStartPositionChanged(String inputId,long timeMs) {
                startPosition=timeMs;
                hasTimeShiftPosition=true;
                setStatus("DVB TimeShift buffer start="+timeMs+" • "+delaySeconds+"s requested");
            }
            @Override public void onTimeShiftCurrentPositionChanged(String inputId,long timeMs) {
                currentPosition=timeMs;
                hasTimeShiftPosition=true;
            }
        });
        loadChannels();
    }
    private TextView label(String msg,int size) {
        TextView v=new TextView(this);
        v.setText(msg);v.setTextColor(0xFFEAF5FF);v.setTextSize(size);v.setPadding(5,4,5,4);
        return v;
    }
    private interface Action {void fire(View v);}
    private void add(LinearLayout row,String s,Action a){
        Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF225475));
        b.setOnClickListener(a::fire);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(2,2,2,2);row.addView(b,p);
    }
    private void loadChannels() {
        try {
            TvInputManager mgr=(TvInputManager)getSystemService(Context.TV_INPUT_SERVICE);
            if(mgr!=null){
                List<TvInputInfo> available=mgr.getTvInputList();
                for(TvInputInfo input:available){
                    String id=input.getId();
                    if(selectedInput.isEmpty() || id.toLowerCase().contains("dtvkit") ||
                        id.toLowerCase().contains("droidlogic"))selectedInput=id;
                }
            }
            try(Cursor c=getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID,TvContract.Channels.COLUMN_DISPLAY_NAME,
                    TvContract.Channels.COLUMN_INPUT_ID},null,null,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER+" ASC")){
                if(c!=null){
                    while(c.moveToNext() && channels.size()<3000){
                        long id=c.getLong(0);
                        String name=c.getString(1),input=c.getString(2);
                        if(input!=null && (input.toLowerCase().contains("droidlogic") ||
                            input.toLowerCase().contains("dtvkit") || input.equals(selectedInput))){
                            channels.add(new Channel(name==null?String.valueOf(id):name,input,
                                TvContract.buildChannelUri(id)));
                        }
                    }
                }
            }catch(SecurityException se){
                setStatus("قائمة القنوات محمية: READ_TV_LISTINGS أو إعدادات المصنع مطلوبة");
            }
            if(channels.isEmpty()){
                channelLabel.setText("No accessible TV channel list");
                setStatus("Aurum may keep channels in its private DB; cannot tune without URI/channel permission.");
            }else{
                channelLabel.setText("Channel 1/"+channels.size()+": "+channels.get(0).name);
            }
        }catch(Exception e){setStatus("Channel discovery: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void change(int diff){
        if(channels.isEmpty())return;
        index=(index+diff+channels.size())%channels.size();
        channelLabel.setText("Channel "+(index+1)+"/"+channels.size()+": "+channels.get(index).name);
        tune();
    }
    private void tune(){
        if(channels.isEmpty()){setStatus("No TV Input channel URI available");return;}
        Channel c=channels.get(index);
        try {
            hasTimeShiftPosition=false;startPosition=-1;currentPosition=-1;
            tv.tune(c.input,c.uri);
            setStatus("TUNE requested: "+c.name+" • "+c.input);
        }catch(Exception e){setStatus("TUNE failed: "+e.getMessage());}
    }
    private void pause() {
        try{tv.timeShiftPause();setStatus("Pause requested; waits for OEM TV Input support");}
        catch(Exception e){setStatus("Pause unavailable: "+e);}
    }
    private void resume() {
        try{tv.timeShiftResume();setStatus("Resume requested");}
        catch(Exception e){setStatus("Resume unavailable: "+e);}
    }
    private void changeDelay(int delta){
        delaySeconds=Math.max(0,Math.min(120,delaySeconds+delta));
        setStatus("Requested DVB video delay: "+delaySeconds+"s • press APPLY");
    }
    private void applyDelay(){
        if(!hasTimeShiftPosition || currentPosition<=0 || startPosition<=0){
            new AlertDialog.Builder(this).setTitle("TimeShift not available")
                .setMessage("ما وصلتنا timestamps من TV Input. كتم الصوت أو سحب الفيديو مو مضمون بدون دعم OEM TimeShift.")
                .setPositiveButton("OK",null).show();
            return;
        }
        long target=Math.max(startPosition,currentPosition-delaySeconds*1000L);
        try {
            tv.timeShiftPause();
            tv.timeShiftSeekTo(target);
            tv.timeShiftResume();
            setStatus("Seek requested to "+target+" (delay "+delaySeconds+"s). Verify visually on DVB.");
        }catch(Exception e){setStatus("TimeShift seek FAILED: "+e);}
    }
    private void setStatus(String s) {if(status!=null)status.setText(s);}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){pause();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT){changeDelay(-1);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT){changeDelay(1);return true;}
        return super.onKeyDown(key,event);
    }
    @Override protected void onDestroy(){try{tv.reset();}catch(Exception ignored){}super.onDestroy();}
}

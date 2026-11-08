package com.gazal.audiosync;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import com.ben10.matchaudio.FtaDvbNative;

/** Read-only check of optional BEN10 FTA shared libraries, no ADB/system mutations. */
public final class NativeDvbCheckActivity extends Activity {
    private TextView detail;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,12,18,12);
        root.setBackgroundColor(0xFF071C30);
        TextView header=new TextView(this);
        header.setText("GAZAL • DVB NATIVE LIBRARY DIAGNOSTICS");
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(21);
        root.addView(header);
        TextView notice=new TextView(this);
        notice.setText("يفحص وجود مكتبات BEN10 ARMv7 وحالتها فقط. لا يلغي نظام الترخيص ولا يضبط أجهزة DVB أو يلتقط بثاً دون إذن.");
        notice.setTextColor(0xFFFFD27C);
        notice.setTextSize(15);
        root.addView(notice);
        detail=new TextView(this);
        detail.setTextColor(0xFFCAE9FF);
        detail.setTextSize(14);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(detail);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button check=new Button(this);
        check.setText("فحص المكتبات و Audio PID");
        check.setOnClickListener(v->runProbe());
        root.addView(check);
        Button finish=new Button(this);
        finish.setText("رجوع");finish.setOnClickListener(v->finish());root.addView(finish);
        runProbe();
    }
    private void runProbe(){
        detail.setText("جار فحص المكتبات...");
        new Thread(()->{
            StringBuilder result=new StringBuilder();
            result.append("Native library bridge:\n");
            boolean loaded=FtaDvbNative.isLoaded();
            result.append(FtaDvbNative.loadStatus()).append("\n\n");
            result.append("ABI: ").append(android.os.Build.SUPPORTED_ABIS.length>0?android.os.Build.SUPPORTED_ABIS[0]:"unknown").append("\n");
            result.append("demux6: ").append(new File("/dev/dvb0.demux6").exists()).append("\n");
            result.append("dvr6: ").append(new File("/dev/dvb0.dvr6").exists()).append("\n");
            if(loaded) {
                boolean valid=false;
                try {
                    valid=com.ben10.matchaudio.online.NativeSecurity.valid();
                    result.append("Original native authorization: ").append(valid ? "VALID" : "UNAVAILABLE").append("\\n");
                }catch(Throwable securityError){
                    result.append("Native security check failed: ")
                        .append(securityError.getClass().getSimpleName()).append("\\n");
                }
                if(!valid) {
                    result.append("DVB FTA capture disabled: original native library is not authorized.\\n");
                } else try {
                    FtaDvbNative bridge = new FtaDvbNative();
                    int pid=bridge.detectActiveAudioPid();
                    result.append("Active audio PID: ")
                        .append(pid>=0?String.format(Locale.US,"%d / 0x%X",pid,pid):"UNAVAILABLE "+pid)
                        .append("\n");
                    result.append("Attached PID: ").append(bridge.getAttachedPid()).append("\n");
                    result.append("Tap generation: ").append(bridge.getTapGeneration()).append("\n");
                }catch(Throwable error) {
                    result.append("Native operation rejected: ")
                        .append(error.getClass().getSimpleName()).append(": ")
                        .append(error.getMessage()).append("\n");
                }
            }
            result.append("\nFull DVB audio capture needs OEM permissions and an authorized native engine. ")
                .append("Existing BEN10 libraries depend on libben10_security.so.");
            final String outcome=result.toString();
            new Handler(Looper.getMainLooper()).post(()->{
                if(!isFinishing())detail.setText(outcome);
            });
        }).start();
    }
}

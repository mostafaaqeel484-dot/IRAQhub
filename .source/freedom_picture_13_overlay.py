#!/usr/bin/env python3
"""Apply Freedom Picture 1.3 ADB development-permission test to 1.2 source."""
from pathlib import Path

root=Path("FreedomPicture_1_2")
manifest=root/"app/src/main/AndroidManifest.xml"
s=manifest.read_text()
assert '<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />' in s
s=s.replace(
    '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />',
    '    <!-- ADB grantable development permissions; NOT shell/root/PQ access. -->\n'
    '    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />\n'
    '    <uses-permission android:name="android.permission.DUMP" />\n'
    '    <uses-permission android:name="android.permission.READ_LOGS" />\n'
    '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />')
manifest.write_text(s)

gradle=root/"app/build.gradle"
s=gradle.read_text()
assert "versionCode 3" in s and "versionName '1.2.0'" in s
gradle.write_text(s.replace("versionCode 3", "versionCode 4").replace(
    "versionName '1.2.0'", "versionName '1.3.0-adb-test'"))

activity=root/"app/src/main/java/com/freedom/picture/MainActivity.java"
s=activity.read_text()
old='        Button diagnose=button("▤  DEVICE DIAGNOSTICS");right.addView(diagnose,lp(-1,40));diagnose.setOnClickListener(v->showDiagnostics());space(right,8);'
assert old in s
s=s.replace(old,old+'\n'+
    '        Button adbCheck=button("⌘  ADB PERMISSION CHECK  /  فحص صلاحيات ADB");\n'
    '        right.addView(adbCheck,lp(-1,40));adbCheck.setOnClickListener(v->showAdbPermissions());space(right,8);')
old='status=text("v1.2 • OFFLINE • Amlogic Vendor PQ • no root / sysfs",12,0xffa9c3df,false);'
assert old in s
s=s.replace(old,'status=text("v1.3 ADB GRANT TEST • OFFLINE • NOT ROOT • NO SYSFS WRITES",12,0xffa9c3df,false);')
old='private void showDiagnostics(){String d=bridge.detect()+"\\n\\nRoot/ADB not used."+'
assert old in s
s=s.replace(old,'private void showDiagnostics(){String d=bridge.detect()+"\\n\\n"+AdbGrantProbe.report(this)+"\\nADB shell/root is NOT embedded in this app."+')
needle='    private void saveProfile(){'
assert needle in s
s=s.replace(needle, '''    private void showAdbPermissions(){
        final String report=AdbGrantProbe.report(this);
        new AlertDialog.Builder(this).setTitle("Freedom Picture • ADB Permission Check")
            .setMessage(report).setPositiveButton("إغلاق",null)
            .setNeutralButton("نسخ التقرير",(d,w)->{
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("Freedom Picture ADB permissions",report));
                Toast.makeText(this,"تم نسخ التقرير",Toast.LENGTH_SHORT).show();
            }).show();
    }
'''+needle)
activity.write_text(s)

probe=root/"app/src/main/java/com/freedom/picture/AdbGrantProbe.java"
probe.write_text(r'''package com.freedom.picture;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import java.util.Arrays;

/** Read-only permission inspection. No root, shell or adb server in this app. */
public final class AdbGrantProbe {
    private static final String[] PERMISSIONS={
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.DUMP",
        "android.permission.READ_LOGS"
    };
    private AdbGrantProbe(){}

    public static String report(Context context){
        StringBuilder out=new StringBuilder();
        String pkg=context.getPackageName();
        PackageManager pm=context.getPackageManager();
        String[] requested={};
        try{
            PackageInfo info=pm.getPackageInfo(pkg,PackageManager.GET_PERMISSIONS);
            if(info.requestedPermissions!=null)requested=info.requestedPermissions;
        } catch(Exception err){out.append("Package manifest check failed: ").append(err).append("\n");}
        out.append("Freedom Picture 1.3 • ADB development permission probe\n");
        out.append("Device: ").append(Build.HARDWARE).append(" / Android ").append(Build.VERSION.RELEASE)
           .append(" | app UID: ").append(Process.myUid()).append("\n\n");
        for(String p:PERMISSIONS){
            boolean declared=Arrays.asList(requested).contains(p);
            boolean granted=pm.checkPermission(p,pkg)==PackageManager.PERMISSION_GRANTED;
            out.append(p).append("\n  declared: ").append(declared)
                .append(" | granted: ").append(granted).append("\n");
        }
        out.append("\nRun on your phone's Termux (ADB device already paired):\n");
        for(String p:PERMISSIONS){
            out.append("adb -s 192.168.0.214:5556 shell pm grant ")
                .append(pkg).append(" ").append(p).append("\n");
        }
        out.append("\nReopen this check afterwards. A grant may be refused by your ROM.\n");
        out.append("NOTE: These are development permissions, NOT ADB shell (uid 2000), ")
           .append("NOT root (uid 0), NOT a sysfs/PQ write grant.\n");
        out.append("No system settings, service transactions or video parameters are changed by this check.");
        return out.toString();
    }
}
''')
print("Created Freedom Picture 1.3 test sources")

package com.smarthub.plus;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;

final class MenuAcceptance31 {
    static void run(Instrumentation test,Context c,Bundle out)throws Exception{
        out.putBoolean("native_menu_enabled",c.getSharedPreferences("smarthub",0).getBoolean("satellite_menu_shortcut",true));
        Intent target=ReceiverNavigation.launcher(c);
        out.putBoolean("explicit_own_launcher",target.getComponent()!=null&&target.getComponent().getPackageName().equals(c.getPackageName())&&target.getComponent().getClassName().equals(MainActivity.class.getName()));
        Activity page=test.startActivitySync(new Intent(c,AppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->{out.putBoolean("down_consumed",page.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU)));out.putBoolean("up_consumed",page.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MENU)));});
        test.waitForIdleSync();
        if(!out.getBoolean("explicit_own_launcher")||!out.getBoolean("down_consumed")||!out.getBoolean("up_consumed"))throw new AssertionError("MENU did not consume the full press");
    }
}

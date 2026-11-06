package com.smarthub.plus;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Receiver remote key tester. Reports only codes delivered to this activity.
 * No key remapping, channel changes, privileges, recordings or background hooks.
 */
public final class RemoteKeyDiagnosticsActivity extends HubActivity {
    private final StringBuilder history = new StringBuilder();
    private TextView events;
    private long started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        started = SystemClock.elapsedRealtime();
        LinearLayout body = page(tx("فحص أزرار ريموت DVB", "DVB remote button tester"),
                tx("اضغط كل زر وشوف كوده وScanCode الفعلي من الجهاز.",
                   "Press each key to see its actual Android key code and scan code."));
        LinearLayout guide = card(body);
        guide.addView(text(tx(
            "افحص: CH+ / CH−، PAGE+ / PAGE−، OK، BACK، INFO، MENU، EPG، FAV، TV/RADIO، LAST، الألوان، الصوت، الأرقام، الأسهم.",
            "Test CH+/CH-, PAGE+/PAGE-, OK, BACK, INFO, MENU, EPG, FAV, TV/RADIO, LAST, colours, volume, digits and arrows."),15,false));
        note(guide, tx(
            "بعض الأزرار مثل POWER وHOME والصوت قد يعالجها النظام قبل وصولها للتطبيق. غيابها هنا لا يثبت أن الريموت معطل. BACK يغلق صفحة الفحص.",
            "The firmware can intercept POWER, HOME or volume before this app. Missing events don't prove the key is broken. BACK leaves this tester."));
        LinearLayout actions = card(body);
        action(actions,tx("مسح النتائج","Clear captured keys"),()->{history.setLength(0);events.setText("");started=SystemClock.elapsedRealtime();});
        events = text(tx("ماكو أحداث بعد — اضغط زر بالريموت", "No events yet — press a remote key"),14,false);
        events.setTextIsSelectable(true);
        card(body).addView(events);
        body.setFocusableInTouchMode(true);
        body.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            String line = String.format(java.util.Locale.ROOT,
                    "%5d ms | %s | key=%d | scan=%d | device=%d | repeat=%d\n",
                    SystemClock.elapsedRealtime()-started,KeyEvent.keyCodeToString(key),
                    key,event.getScanCode(),event.getDeviceId(),event.getRepeatCount());
            if (history.length() > 9000) history.delete(0, history.length()-4500);
            history.insert(0,line);
            if (events != null) events.setText(history);
        }
        // Consume keys in the tester only; the receiver menus are not affected.
        return true;
    }
}

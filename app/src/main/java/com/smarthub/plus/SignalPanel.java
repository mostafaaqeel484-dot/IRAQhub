package com.smarthub.plus;
import android.app.Activity;import android.content.Intent;
final class SignalPanel {static void show(Activity activity){activity.startActivity(new Intent(activity,SignalInformationActivity.class));}}

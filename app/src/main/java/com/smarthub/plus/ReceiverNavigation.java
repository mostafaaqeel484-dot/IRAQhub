package com.smarthub.plus;

import android.content.*;

/** Explicit public OEM activities, with no tuning commands or writes to its channel database. */
final class ReceiverNavigation {
    static final String PACKAGE="com.tosmart.aurum";
    static Intent television(){return new Intent("android.intent.action.home").setPackage(PACKAGE);}
    static Intent guide(){return new Intent("com.tosmart.action.epg_pro").setPackage(PACKAGE);}
    static Intent menu(){return new Intent("com.tosmart.home.menu.navigation").setPackage(PACKAGE);}
    static Intent originalHome(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(PACKAGE);}
    static Intent launcher(Context c){return new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static boolean openLauncher(Context c){return open(c,launcher(c));}
    static boolean available(Context c,Intent i){return c.getPackageManager().resolveActivity(i,0)!=null;}
    static boolean open(Context c,Intent i){try{c.startActivity(new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(ActivityNotFoundException|SecurityException e){return false;}}
}

package com.smarthub.plus;

import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Only activities Android exposes as launchable, preferring the TV entry for each package. */
final class AppCatalogue {
    static final class App {final String label,pkg;final ComponentName component;final ResolveInfo info;App(String l,ResolveInfo r){label=l;info=r;pkg=r.activityInfo.packageName;component=new ComponentName(pkg,r.activityInfo.name);}}
    static List<App> load(Context c){
        PackageManager pm=c.getPackageManager();LinkedHashMap<String,App> apps=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            List<ResolveInfo> rows=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0);
            for(ResolveInfo r:rows){ActivityInfo a=r.activityInfo;if(a==null||!a.exported||!a.enabled||!a.applicationInfo.enabled||a.packageName.equals(c.getPackageName())||apps.containsKey(a.packageName))continue;if(a.permission!=null&&pm.checkPermission(a.permission,c.getPackageName())!=PackageManager.PERMISSION_GRANTED)continue;
                String label;try{label=String.valueOf(r.loadLabel(pm));}catch(Exception e){label=a.packageName;}if(label.trim().isEmpty())label=a.packageName;apps.put(a.packageName,new App(label,r));}
        }
        List<App> result=new ArrayList<>(apps.values());result.sort(Comparator.comparing((App a)->a.label,String.CASE_INSENSITIVE_ORDER).thenComparing(a->a.pkg));return result;
    }
    static Intent launch(App app){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);}
}

package com.smarthub.plus;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact two-column remote-focusable receiver menus, not full-width button stacks. */
final class ReceiverTileMenu {
 static final class Tile {
  final String number,title,description;final Runnable action;
  Tile(String n,String title,String description,Runnable action){
   this.number=n;this.title=title;this.description=description;this.action=action;
  }
 }
 private ReceiverTileMenu(){}
 static void pair(HubActivity host,LinearLayout body,Tile first,Tile second){
  LinearLayout line=host.row();line.setGravity(Gravity.TOP);
  line.setLayoutDirection(host.ar?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.bottomMargin=host.dp(12);body.addView(line,params);
  add(host,line,first);
  if(second!=null)add(host,line,second);
  else{
   View blank=new View(host);line.addView(blank,new LinearLayout.LayoutParams(0,host.dp(128),1));
  }
 }
 private static void add(HubActivity host,LinearLayout row,Tile item){
  LinearLayout box=host.col();
  box.setGravity(Gravity.CENTER_VERTICAL);
  box.setPadding(host.dp(17),host.dp(12),host.dp(14),host.dp(12));
  box.setFocusable(true);box.setClickable(true);
  box.setContentDescription(item.number+" "+item.title+". "+item.description);
  StateListDrawable drawable=new StateListDrawable();
  GradientDrawable idle=shape(host,0xff1b2b41,0xff35455c);
  GradientDrawable focused=shape(host,0xff304b60,0xffffd86c);
  drawable.addState(new int[]{android.R.attr.state_focused},focused);
  drawable.addState(new int[]{android.R.attr.state_pressed},focused);
  drawable.addState(new int[]{},idle);box.setBackground(drawable);
  TextView number=host.text(item.number,14,true);number.setTextColor(0xffffd86c);
  number.setGravity(Gravity.START);box.addView(number);
  TextView title=host.text(item.title,18,true);
  title.setMaxLines(2);title.setTextColor(0xfff5f9ff);box.addView(title);
  TextView caption=host.text(item.description,12,false);
  caption.setMaxLines(2);caption.setTextColor(0xffa9c2ce);box.addView(caption);
  box.setOnClickListener(v->item.action.run());
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,host.dp(134),1);
  params.setMargins(host.dp(5),0,host.dp(5),0);row.addView(box,params);
 }
 private static GradientDrawable shape(HubActivity host,int fill,int stroke){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
    new int[]{fill,0xff101923});d.setCornerRadius(host.dp(18));
  d.setStroke(host.dp(1),stroke);return d;
 }
}

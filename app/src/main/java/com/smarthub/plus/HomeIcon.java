package com.smarthub.plus;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Small local vector-style TV icons, with no downloads or third-party branding. */
final class HomeIcon extends Drawable {
 static final int PLAY=0,CHANNELS=1,SATELLITE=2,GUIDE=3,SPORT=4,SETTINGS=5,WIFI=6,THEME=7,HOME=8,APPS=9,PLUS=10;
 private final int kind,normalColor;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 HomeIcon(int k,int color){kind=k;normalColor=color;p.setColor(color);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setStyle(Paint.Style.STROKE);switch(kind){
 case PLAY:c.drawRoundRect(2,5,22,20,3,3,p);c.drawLine(8,1,12,5,p);c.drawLine(16,1,12,5,p);Path t=new Path();t.moveTo(10,9);t.lineTo(16,12.5f);t.lineTo(10,16);t.close();c.drawPath(t,p);break;
 case CHANNELS:for(int i=0;i<3;i++){c.drawRoundRect(2,3+i*7,6,7+i*7,1,1,p);c.drawLine(10,5+i*7,22,5+i*7,p);}break;
 case SATELLITE:Path d=new Path();d.moveTo(4,4);d.cubicTo(1,16,9,22,18,18);d.close();c.drawPath(d,p);c.drawLine(10,12,18,4,p);c.drawCircle(18,4,1.3f,p);c.drawArc(13,1,23,11,-90,85,false,p);c.drawLine(11,20,10,23,p);break;
 case GUIDE:c.drawRoundRect(2,4,22,22,2,2,p);c.drawLine(2,10,22,10,p);c.drawLine(7,2,7,6,p);c.drawLine(17,2,17,6,p);for(int x=7;x<=17;x+=5)for(int y=14;y<=18;y+=4)c.drawPoint(x,y,p);break;
 case SPORT:c.drawCircle(12,12,10,p);Path ball=new Path();for(int i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;float x=12+(float)Math.cos(a)*4,y=12+(float)Math.sin(a)*4;if(i==0)ball.moveTo(x,y);else ball.lineTo(x,y);c.drawLine(x,y,12+(float)Math.cos(a)*9.8f,12+(float)Math.sin(a)*9.8f,p);}ball.close();c.drawPath(ball,p);break;
 case SETTINGS:c.drawCircle(12,12,6,p);c.drawCircle(12,12,2,p);for(int i=0;i<8;i++){double a=Math.PI*i/4;c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}break;
 case WIFI:c.drawArc(1,2,23,22,220,100,false,p);c.drawArc(5,7,19,21,220,100,false,p);c.drawArc(9,12,15,18,220,100,false,p);c.drawCircle(12,19,1,p);break;
 case THEME:c.drawCircle(12,12,10,p);p.setStyle(Paint.Style.FILL);c.drawArc(2,2,22,22,-90,180,true,p);break;
 case HOME:Path h=new Path();h.moveTo(2,11);h.lineTo(12,2);h.lineTo(22,11);h.moveTo(5,9);h.lineTo(5,22);h.lineTo(19,22);h.lineTo(19,9);h.moveTo(10,22);h.lineTo(10,15);h.lineTo(14,15);h.lineTo(14,22);c.drawPath(h,p);break;
 case APPS:for(int x=3;x<20;x+=11)for(int y=3;y<20;y+=11)c.drawRoundRect(x,y,x+7,y+7,1.3f,1.3f,p);break;
 default:c.drawLine(12,4,12,20,p);c.drawLine(4,12,20,12,p);
 }c.restoreToCount(save);}
 @Override public boolean isStateful(){return true;}
 @Override protected boolean onStateChange(int[] states){boolean focus=false;for(int state:states)if(state==android.R.attr.state_focused||state==android.R.attr.state_pressed)focus=true;int color=focus?0xff141625:normalColor;if(p.getColor()==color)return false;p.setColor(color);invalidateSelf();return true;}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

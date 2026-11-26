package com.iraqbiss.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

/**
 * Lightweight layered Night Scene inspired by the visual direction of tvOS-Launcher
 * by techgenius1 (Apache-2.0). Original custom rendering, no reused proprietary assets.
 * Works on 32-bit Android TV without allocating bitmaps.
 */
final class SceneBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] starsX=new float[57];
    private final float[] starsY=new float[57];
    private final float[] starsR=new float[57];
    private int scene=0;
    SceneBackdrop(Context c) {
        super(c);
        Random r=new Random(487211);
        for(int i=0;i<starsX.length;i++){
            starsX[i]=r.nextFloat();starsY[i]=.015f+r.nextFloat()*.67f;
            starsR[i]=.3f+r.nextFloat()*.9f;
        }
    }
    void setScene(int s){scene=Math.abs(s)%3;invalidate();}
    private int rgb(int r,int g,int b){return Color.rgb(r,g,b);}
    @Override protected void onDraw(Canvas canvas){
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        int top=scene==0?rgb(8,17,38):scene==1?rgb(38,25,60):rgb(3,31,50);
        int mid=scene==0?rgb(41,54,89):scene==1?rgb(93,59,87):rgb(21,62,83);
        int bottom=scene==0?rgb(5,14,30):scene==1?rgb(17,19,40):rgb(3,20,31);
        paint.setShader(new LinearGradient(0,0,0,h,
            new int[]{top,mid,bottom},new float[]{0,.56f,1},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        float glowX=w*(scene==1?.78f:.68f),glowY=h*.39f;
        paint.setShader(new RadialGradient(glowX,glowY,w*.54f,
            new int[]{scene==1?0x664D7AB2:0x3A78A6EA,0x102A4774,0x002A4774},
            new float[]{0,.48f,1},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        if(scene!=1){
            for(int i=0;i<starsX.length;i++){
                paint.setColor(Color.argb((int)(72+starsR[i]*95),230,239,255));
                canvas.drawCircle(w*starsX[i],h*starsY[i],starsR[i]*Math.max(1f,w/1600f),paint);
            }
        }
        ridge(canvas,w,h,.69f,0x613A4A6A,0.047f);
        ridge(canvas,w,h,.77f,0xBA182C49,0.031f);
        ridge(canvas,w,h,.86f,0xE7091B33,0.054f);
        paint.setShader(new LinearGradient(0,h*.56f,0,h,
            0x00041020,0xBA050B1A,Shader.TileMode.CLAMP));
        canvas.drawRect(0,h*.56f,w,h,paint);paint.setShader(null);
    }
    private void ridge(Canvas c,int w,int h,float y,int color,float amplitude){
        Path path=new Path();path.moveTo(0,h);
        path.lineTo(0,h*y);
        float[] noise={-.1f,.32f,-.21f,.77f,.15f,-.48f,.29f,-.3f,.6f,.04f,-.25f,.2f,.02f};
        for(int i=0;i<noise.length;i++){
            float x=i*(w/(float)(noise.length-1));
            float yy=h*(y-noise[i]*amplitude);
            path.lineTo(x,yy);
        }
        path.lineTo(w,h);path.close();paint.setColor(color);c.drawPath(path,paint);
    }
}

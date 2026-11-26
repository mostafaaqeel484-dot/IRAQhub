package com.iraqbiss.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.widget.ImageView;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lean 32-bit-safe image pipeline: HTTPS only, bounded decoding, disk and memory cache. */
final class ImageLoader {
    private static final ExecutorService THREADS=Executors.newFixedThreadPool(3);
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(12*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final Context app;
    private final File folder;
    ImageLoader(Context context) {
        app=context.getApplicationContext();
        folder=new File(app.getCacheDir(),"feed_art");
        if(!folder.exists())folder.mkdirs();
    }
    private static String hash(String str){
        try {byte[] sum=MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8"));StringBuilder b=new StringBuilder();
            for(byte c:sum)b.append(String.format(java.util.Locale.US,"%02x",c & 255));
            return b.toString().substring(0,24);
        }catch(Exception e){return Integer.toHexString(str.hashCode());}
    }
    static boolean allowed(String uri) {
        if(uri==null || !uri.startsWith("https://") || uri.length()>1800)return false;
        try{
            URL url=new URL(uri);String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
            return !host.isEmpty() && !host.equals("localhost") && !host.endsWith(".local")
                && !host.endsWith(".internal") && !host.endsWith(".test")
                && !host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")
                && !host.contains(":");
        }catch(Exception e){return false;}
    }
    void into(ImageView target,String url){
        target.setTag(url);
        target.setImageDrawable(null);
        if(!allowed(url)) {target.setVisibility(android.view.View.GONE);return;}
        target.setVisibility(android.view.View.VISIBLE);
        Bitmap existing=MEMORY.get(url);
        if(existing!=null){target.setImageBitmap(existing);return;}
        THREADS.execute(()->{
            Bitmap b=obtain(url);
            target.post(()->{
                if(url.equals(target.getTag())){
                    if(b!=null)target.setImageBitmap(b);
                    else target.setVisibility(android.view.View.GONE);
                }
            });
        });
    }
    private Bitmap obtain(String url){
        synchronized(MEMORY){Bitmap v=MEMORY.get(url);if(v!=null)return v;}
        File disk=new File(folder,hash(url)+".jpg");
        if(!disk.isFile())download(url,disk);
        Bitmap b=decode(disk);
        if(b!=null)MEMORY.put(url,b);
        return b;
    }
    private Bitmap decode(File f){
        if(!f.exists())return null;
        BitmapFactory.Options probe=new BitmapFactory.Options();probe.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getAbsolutePath(),probe);
        if(probe.outWidth<90||probe.outHeight<60)return null;
        int sample=1;
        while(probe.outWidth/sample>900 || probe.outHeight/sample>650)sample*=2;
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;
        opt.inPreferredConfig=Bitmap.Config.RGB_565;
        try{return BitmapFactory.decodeFile(f.getAbsolutePath(),opt);}catch(OutOfMemoryError e){return null;}
    }
    private void download(String uri,File file){
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(uri).openConnection();
            c.setConnectTimeout(6000);c.setReadTimeout(7000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; IraqBISS/0.3)");
            int code=c.getResponseCode();
            if(code!=200 || !String.valueOf(c.getContentType()).toLowerCase(java.util.Locale.ROOT).startsWith("image/"))return;
            int size=c.getContentLength();
            if(size>1500000)return;
            File tmp=new File(folder,hash(uri)+".tmp");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buffer=new byte[8192];int n,total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>1500000)throw new Exception("image oversized");out.write(buffer,0,n);}
            }
            if(!tmp.renameTo(file)){tmp.delete();return;}
            trim();
        }catch(Exception ignored){
            new File(folder,hash(uri)+".tmp").delete();
        }finally{if(c!=null)c.disconnect();}
    }
    private void trim(){
        File[] files=folder.listFiles((dir,name)->name.endsWith(".jpg"));
        if(files==null || files.length<=120)return;
        java.util.Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));
        for(int k=0;k<files.length-100;k++)files[k].delete();
    }
}

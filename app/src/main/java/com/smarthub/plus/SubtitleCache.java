package com.smarthub.plus;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Downloaded immutable file IDs are reused only after an online search chooses them again. */
final class SubtitleCache {
 static synchronized String load(Context c,long id){File f=new File(new File(c.getCacheDir(),"subtitles"),id+".srt");if(!f.isFile()||System.currentTimeMillis()-f.lastModified()>86400000||f.length()>5*1024*1024)return null;try{return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}catch(Exception e){return null;}}
 static synchronized void save(Context c,long id,String text)throws IOException{File dir=new File(c.getCacheDir(),"subtitles");if(!dir.isDirectory()&&!dir.mkdirs())return;File f=new File(dir,id+".srt");try(FileOutputStream out=new FileOutputStream(f)){out.write(text.getBytes(StandardCharsets.UTF_8));}File[] files=dir.listFiles((d,n)->n.matches("[0-9]+\\.srt"));if(files!=null&&files.length>20){Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(int i=0;i<files.length-20;i++)files[i].delete();}}
}
